package com.wudcompress.android.core;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Pure-Java implementation of the WudCompress v1.0 WUD <-> WUX algorithm.
 *
 * The WUX on-disk layout is preserved:
 *  - 32-byte little-endian header
 *  - uint32 sector index table
 *  - sector data aligned to sectorSize
 *
 * Unlike the original O(n^2) duplicate lookup, this implementation uses a
 * compact open-addressing hash table. If the original 256-bit cheap hash
 * collides, sector bytes are compared before reusing a sector.
 */
public final class WudEngine {
    private WudEngine() {}

    public static final int MODE_WUD_TO_WUX = 0;
    public static final int MODE_WUX_TO_WUD = 1;

    public static final int OK = 0;
    public static final int ERR_INPUT = -1;
    public static final int ERR_NOT_SEEKABLE = -2;
    public static final int ERR_OUTPUT = -3;
    public static final int ERR_IO = -4;
    public static final int ERR_VERIFY = -5;
    public static final int ERR_MEMORY = -6;
    public static final int ERR_FORMAT = -7;

    private static final int WUX_MAGIC_0 = 0x30585557; // bytes W U X 0
    private static final int WUX_MAGIC_1 = 0x1099D02E;
    private static final int HEADER_SIZE = 32;
    private static final int DEFAULT_SECTOR_SIZE = 0x8000;
    private static final int HASH_SIZE = 32;
    private static final int VERIFY_BUFFER_SIZE = 1024 * 1024 + 19;

    public enum Stage {
        READING,
        COMPRESSING,
        DECOMPRESSING,
        VERIFYING,
        DONE
    }

    public interface ProgressListener {
        void onProgress(Stage stage, int perMille);
    }

    /** Random-access file abstraction. Implementations may be read-only. */
    public interface RandomAccessFileLike {
        long size() throws IOException;
        int read(long position, byte[] buffer, int offset, int length) throws IOException;
        void write(long position, byte[] buffer, int offset, int length) throws IOException;
        void truncate(long size) throws IOException;
        void force() throws IOException;
    }

    public static int detect(RandomAccessFileLike input) {
        try {
            ImageReader image = ImageReader.open(input);
            return image.compressed ? MODE_WUX_TO_WUD : MODE_WUD_TO_WUX;
        } catch (IOException | IllegalArgumentException e) {
            return ERR_INPUT;
        } catch (OutOfMemoryError e) {
            return ERR_MEMORY;
        }
    }

    public static int process(
            RandomAccessFileLike input,
            RandomAccessFileLike output,
            boolean verify,
            ProgressListener listener) {
        try {
            ImageReader image = ImageReader.open(input);
            output.truncate(0);

            boolean ok;
            if (image.compressed) {
                ok = decompress(image, output, listener);
            } else {
                ok = compress(image, output, listener);
            }
            if (!ok) return ERR_IO;

            output.force();
            if (verify) {
                if (!validate(input, output, listener)) {
                    return ERR_VERIFY;
                }
            }
            notifyProgress(listener, Stage.DONE, 1000);
            return OK;
        } catch (OutOfMemoryError e) {
            return ERR_MEMORY;
        } catch (IllegalArgumentException e) {
            return ERR_FORMAT;
        } catch (IOException e) {
            return ERR_IO;
        }
    }

    private static boolean compress(
            ImageReader input,
            RandomAccessFileLike output,
            ProgressListener listener) throws IOException {
        final long inputSize = input.uncompressedSize;
        final int sectorSize = DEFAULT_SECTOR_SIZE;
        final long sectorCountLong = ceilDiv(inputSize, sectorSize);
        if (sectorCountLong <= 0 || sectorCountLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Unsupported sector count");
        }
        final int sectorCount = (int) sectorCountLong;

        writeHeader(output, sectorSize, inputSize, 0);
        final long indexOffset = HEADER_SIZE;
        final long indexBytes = (long) sectorCount * 4L;
        final long sectorArrayOffset = align(indexOffset + indexBytes, sectorSize);

        int[] indexTable = new int[sectorCount];
        byte[] hashes = new byte[Math.multiplyExact(sectorCount, HASH_SIZE)];
        int hashTableSize = hashTableCapacity(sectorCount);
        int[] hashSlots = new int[hashTableSize]; // uniqueIndex + 1; 0 means empty
        int slotMask = hashTableSize - 1;

        byte[] sector = new byte[sectorSize];
        byte[] storedSector = new byte[sectorSize];
        byte[] hash = new byte[HASH_SIZE];

        int uniqueCount = 0;
        for (int i = 0; i < sectorCount; i++) {
            Arrays.fill(sector, (byte) 0);
            long logicalOffset = (long) i * sectorSize;
            int wanted = (int) Math.min((long) sectorSize, inputSize - logicalOffset);
            int got = input.read(logicalOffset, sector, 0, wanted);
            if (got != wanted) throw new EOFException("Short WUD read");

            calculateOriginalHash(sector, sectorSize, hash);
            int probe = mixHash(hash) & slotMask;
            int reuse = -1;
            int emptySlot = -1;

            for (int probes = 0; probes < hashTableSize; probes++) {
                int slotValue = hashSlots[probe];
                if (slotValue == 0) {
                    emptySlot = probe;
                    break;
                }

                int candidate = slotValue - 1;
                int candidateHashOffset = candidate * HASH_SIZE;
                if (hashEquals(hashes, candidateHashOffset, hash)) {
                    long candidateOffset = sectorArrayOffset + (long) candidate * sectorSize;
                    readExact(output, candidateOffset, storedSector, 0, sectorSize);
                    if (Arrays.equals(sector, storedSector)) {
                        reuse = candidate;
                        break;
                    }
                }
                probe = (probe + 1) & slotMask;
            }

            if (reuse >= 0) {
                indexTable[i] = reuse;
            } else {
                if (emptySlot < 0) throw new IllegalStateException("Hash table full");
                long writeOffset = sectorArrayOffset + (long) uniqueCount * sectorSize;
                output.write(writeOffset, sector, 0, sectorSize);
                System.arraycopy(hash, 0, hashes, uniqueCount * HASH_SIZE, HASH_SIZE);
                hashSlots[emptySlot] = uniqueCount + 1;
                indexTable[i] = uniqueCount;
                uniqueCount++;
            }

            int progress = (int) (((long) (i + 1) * 1000L) / sectorCount);
            notifyProgress(listener, Stage.COMPRESSING, progress);
        }

        int tableChunkBytes = sectorCount <= 262144 ? Math.max(4, sectorCount * 4) : 1024 * 1024;
        byte[] tableBytes = new byte[tableChunkBytes];
        int entriesPerChunk = Math.max(1, tableBytes.length / 4);
        int pos = 0;
        while (pos < sectorCount) {
            int entries = Math.min(entriesPerChunk, sectorCount - pos);
            int byteCount = entries * 4;
            ByteBuffer bb = ByteBuffer.wrap(tableBytes, 0, byteCount).order(ByteOrder.LITTLE_ENDIAN);
            for (int j = 0; j < entries; j++) bb.putInt(indexTable[pos + j]);
            output.write(indexOffset + (long) pos * 4L, tableBytes, 0, byteCount);
            pos += entries;
        }

        long finalSize = sectorArrayOffset + (long) uniqueCount * sectorSize;
        output.truncate(finalSize);
        notifyProgress(listener, Stage.COMPRESSING, 1000);
        return true;
    }

    private static boolean decompress(
            ImageReader input,
            RandomAccessFileLike output,
            ProgressListener listener) throws IOException {
        final long inputSize = input.uncompressedSize;
        final int chunkSize = DEFAULT_SECTOR_SIZE;
        byte[] buffer = new byte[chunkSize];
        long offset = 0;

        while (offset < inputSize) {
            int bytes = (int) Math.min((long) chunkSize, inputSize - offset);
            int got = input.read(offset, buffer, 0, bytes);
            if (got != bytes) throw new EOFException("Short WUX read");
            output.write(offset, buffer, 0, bytes);
            offset += bytes;
            int progress = inputSize == 0 ? 1000 : (int) ((offset * 1000L) / inputSize);
            notifyProgress(listener, Stage.DECOMPRESSING, progress);
        }

        output.truncate(inputSize);
        notifyProgress(listener, Stage.DECOMPRESSING, 1000);
        return true;
    }

    private static boolean validate(
            RandomAccessFileLike first,
            RandomAccessFileLike second,
            ProgressListener listener) throws IOException {
        ImageReader a = ImageReader.open(first);
        ImageReader b = ImageReader.open(second);
        if (a.uncompressedSize != b.uncompressedSize) return false;

        byte[] aBuf = new byte[VERIFY_BUFFER_SIZE];
        byte[] bBuf = new byte[VERIFY_BUFFER_SIZE];
        long offset = 0;
        long size = a.uncompressedSize;

        while (offset < size) {
            int bytes = (int) Math.min((long) VERIFY_BUFFER_SIZE, size - offset);
            int aRead = a.read(offset, aBuf, 0, bytes);
            int bRead = b.read(offset, bBuf, 0, bytes);
            if (aRead != bytes || bRead != bytes) return false;
            for (int i = 0; i < bytes; i++) {
                if (aBuf[i] != bBuf[i]) return false;
            }
            offset += bytes;
            int progress = size == 0 ? 1000 : (int) ((offset * 1000L) / size);
            notifyProgress(listener, Stage.VERIFYING, progress);
        }
        notifyProgress(listener, Stage.VERIFYING, 1000);
        return true;
    }

    private static void writeHeader(
            RandomAccessFileLike output,
            int sectorSize,
            long uncompressedSize,
            int flags) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(WUX_MAGIC_0);
        header.putInt(WUX_MAGIC_1);
        header.putInt(sectorSize);
        header.putInt(0); // MSVC struct padding before uint64
        header.putLong(uncompressedSize);
        header.putInt(flags);
        header.putInt(0); // trailing struct padding
        output.write(0, header.array(), 0, HEADER_SIZE);
    }

    private static void calculateOriginalHash(byte[] data, int length, byte[] out) {
        Arrays.fill(out, (byte) 0);
        for (int i = 0; i < length; i++) {
            int value = data[i] & 0xFF;
            int p0 = i & 31;
            int p1 = (i + 7) & 31;
            out[p0] = (byte) ((out[p0] & 0xFF) ^ value);
            out[p1] = (byte) (((out[p1] & 0xFF) + value) & 0xFF);
        }
    }

    private static boolean hashEquals(byte[] packedHashes, int offset, byte[] hash) {
        for (int i = 0; i < HASH_SIZE; i++) {
            if (packedHashes[offset + i] != hash[i]) return false;
        }
        return true;
    }

    private static int mixHash(byte[] hash) {
        int h = 0x811C9DC5;
        for (byte b : hash) {
            h ^= (b & 0xFF);
            h *= 0x01000193;
        }
        h ^= (h >>> 16);
        return h;
    }

    private static int hashTableCapacity(int entries) {
        long wanted = Math.max(16L, (long) entries * 2L);
        int cap = 1;
        while (cap < wanted && cap > 0) cap <<= 1;
        if (cap <= 0) throw new OutOfMemoryError("WUX hash table too large");
        return cap;
    }

    private static long align(long value, long alignment) {
        long remainder = value % alignment;
        return remainder == 0 ? value : value + (alignment - remainder);
    }

    private static long ceilDiv(long value, long divisor) {
        return (value + divisor - 1L) / divisor;
    }

    private static void readExact(
            RandomAccessFileLike file,
            long position,
            byte[] buffer,
            int offset,
            int length) throws IOException {
        int done = 0;
        while (done < length) {
            int r = file.read(position + done, buffer, offset + done, length - done);
            if (r < 0) throw new EOFException();
            if (r == 0) throw new EOFException();
            done += r;
        }
    }

    private static void notifyProgress(ProgressListener listener, Stage stage, int perMille) {
        if (listener != null) listener.onProgress(stage, Math.max(0, Math.min(1000, perMille)));
    }

    private static final class ImageReader {
        final RandomAccessFileLike file;
        final boolean compressed;
        final long uncompressedSize;
        final int sectorSize;
        final int[] indexTable;
        final long sectorArrayOffset;

        private ImageReader(
                RandomAccessFileLike file,
                boolean compressed,
                long uncompressedSize,
                int sectorSize,
                int[] indexTable,
                long sectorArrayOffset) {
            this.file = file;
            this.compressed = compressed;
            this.uncompressedSize = uncompressedSize;
            this.sectorSize = sectorSize;
            this.indexTable = indexTable;
            this.sectorArrayOffset = sectorArrayOffset;
        }

        static ImageReader open(RandomAccessFileLike file) throws IOException {
            long size = file.size();
            if (size < HEADER_SIZE) throw new IllegalArgumentException("Input too short");

            byte[] rawHeader = new byte[HEADER_SIZE];
            readExact(file, 0, rawHeader, 0, HEADER_SIZE);
            ByteBuffer bb = ByteBuffer.wrap(rawHeader).order(ByteOrder.LITTLE_ENDIAN);
            int magic0 = bb.getInt();
            int magic1 = bb.getInt();

            if (magic0 != WUX_MAGIC_0 || magic1 != WUX_MAGIC_1) {
                return new ImageReader(file, false, size, 0, null, 0);
            }

            int sectorSize = bb.getInt();
            bb.getInt(); // padding
            long uncompressedSize = bb.getLong();
            bb.getInt(); // flags
            bb.getInt(); // padding

            if (sectorSize < 0x100 || sectorSize >= 0x10000000) {
                throw new IllegalArgumentException("Invalid WUX sector size");
            }
            if (uncompressedSize < 0) {
                throw new IllegalArgumentException("Invalid WUX size");
            }

            long countLong = ceilDiv(uncompressedSize, sectorSize);
            if (countLong > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Too many sectors");
            }
            int count = (int) countLong;
            long indexOffset = HEADER_SIZE;
            long indexBytes = countLong * 4L;
            long sectorArrayOffset = align(indexOffset + indexBytes, sectorSize);
            if (sectorArrayOffset > size) {
                throw new IllegalArgumentException("Truncated WUX table");
            }

            int[] table = new int[count];
            int tableChunkBytes = count <= 262144 ? Math.max(4, count * 4) : 1024 * 1024;
            byte[] chunk = new byte[tableChunkBytes];
            int entriesPerChunk = Math.max(1, chunk.length / 4);
            int pos = 0;
            while (pos < count) {
                int entries = Math.min(entriesPerChunk, count - pos);
                int bytes = entries * 4;
                readExact(file, indexOffset + (long) pos * 4L, chunk, 0, bytes);
                ByteBuffer ib = ByteBuffer.wrap(chunk, 0, bytes).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < entries; i++) table[pos + i] = ib.getInt();
                pos += entries;
            }

            long availableSectors = (size - sectorArrayOffset) / sectorSize;
            for (int idx : table) {
                long unsigned = idx & 0xFFFFFFFFL;
                if (unsigned >= availableSectors) {
                    throw new IllegalArgumentException("WUX index points outside file");
                }
            }

            return new ImageReader(file, true, uncompressedSize, sectorSize, table, sectorArrayOffset);
        }

        int read(long logicalOffset, byte[] buffer, int offset, int length) throws IOException {
            if (logicalOffset < 0 || logicalOffset >= uncompressedSize || length <= 0) return 0;
            int wanted = (int) Math.min((long) length, uncompressedSize - logicalOffset);
            if (!compressed) {
                int total = 0;
                while (total < wanted) {
                    int r = file.read(logicalOffset + total, buffer, offset + total, wanted - total);
                    if (r <= 0) break;
                    total += r;
                }
                return total;
            }

            int total = 0;
            while (total < wanted) {
                long logical = logicalOffset + total;
                int sectorOffset = (int) (logical % sectorSize);
                int logicalSector = (int) (logical / sectorSize);
                int bytes = Math.min(sectorSize - sectorOffset, wanted - total);
                long storedSector = indexTable[logicalSector] & 0xFFFFFFFFL;
                long physical = sectorArrayOffset + storedSector * sectorSize + sectorOffset;
                int done = 0;
                while (done < bytes) {
                    int r = file.read(physical + done, buffer, offset + total + done, bytes - done);
                    if (r <= 0) return total + done;
                    done += r;
                }
                total += bytes;
            }
            return total;
        }
    }
}
