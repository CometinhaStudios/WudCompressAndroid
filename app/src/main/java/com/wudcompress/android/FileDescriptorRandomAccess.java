package com.wudcompress.android;

import android.system.ErrnoException;
import android.system.Os;

import com.wudcompress.android.core.WudEngine;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * Random-access adapter for Android ParcelFileDescriptor-backed files.
 * Uses the public android.system.Os API (API 21+) and does not own/close the FD.
 */
final class FileDescriptorRandomAccess implements WudEngine.RandomAccessFileLike {
    private final FileDescriptor fd;
    private final boolean writable;

    private FileDescriptorRandomAccess(FileDescriptor fd, boolean writable) {
        this.fd = fd;
        this.writable = writable;
    }

    static FileDescriptorRandomAccess forRead(FileDescriptor fd) {
        return new FileDescriptorRandomAccess(fd, false);
    }

    static FileDescriptorRandomAccess forReadWrite(FileDescriptor fd) {
        return new FileDescriptorRandomAccess(fd, true);
    }

    @Override
    public long size() throws IOException {
        try {
            return Os.fstat(fd).st_size;
        } catch (ErrnoException e) {
            throw io(e);
        }
    }

    @Override
    public int read(long position, byte[] buffer, int offset, int length) throws IOException {
        try {
            return Os.pread(fd, buffer, offset, length, position);
        } catch (ErrnoException e) {
            throw io(e);
        }
    }

    @Override
    public void write(long position, byte[] buffer, int offset, int length) throws IOException {
        if (!writable) throw new IOException("File is read-only");
        int done = 0;
        while (done < length) {
            try {
                int written = Os.pwrite(fd, buffer, offset + done, length - done, position + done);
                if (written <= 0) throw new IOException("Short write");
                done += written;
            } catch (ErrnoException e) {
                throw io(e);
            }
        }
    }

    @Override
    public void truncate(long size) throws IOException {
        if (!writable) throw new IOException("File is read-only");
        try {
            Os.ftruncate(fd, size);
        } catch (ErrnoException e) {
            throw io(e);
        }
    }

    @Override
    public void force() throws IOException {
        if (!writable) return;
        try {
            Os.fsync(fd);
        } catch (ErrnoException e) {
            throw io(e);
        }
    }

    private static IOException io(ErrnoException e) {
        return new IOException(e.getMessage(), e);
    }
}
