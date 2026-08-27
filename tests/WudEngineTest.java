import com.wudcompress.android.core.WudEngine;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;

public class WudEngineTest {
    static class Raf implements WudEngine.RandomAccessFileLike, AutoCloseable {
        final RandomAccessFile raf;
        final FileChannel ch;
        final boolean writable;
        Raf(File f, String mode) throws IOException {
            raf = new RandomAccessFile(f, mode);
            ch = raf.getChannel();
            writable = mode.contains("w");
        }
        public long size() throws IOException { return ch.size(); }
        public int read(long p, byte[] b, int o, int l) throws IOException {
            ByteBuffer bb = ByteBuffer.wrap(b, o, l);
            int total=0;
            while(bb.hasRemaining()) {
                int r=ch.read(bb,p+total);
                if(r<0) return total==0 ? -1:total;
                if(r==0) break;
                total+=r;
            }
            return total;
        }
        public void write(long p, byte[] b, int o, int l) throws IOException {
            if(!writable) throw new IOException("ro");
            ByteBuffer bb=ByteBuffer.wrap(b,o,l); int total=0;
            while(bb.hasRemaining()) { int w=ch.write(bb,p+total); if(w<=0) throw new IOException(); total+=w; }
        }
        public void truncate(long n) throws IOException { ch.truncate(n); }
        public void force() throws IOException { ch.force(false); }
        public void close() throws IOException { raf.close(); }
    }

    static String sha(File f) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(var in=Files.newInputStream(f.toPath())) {
            byte[] b=new byte[65536]; int n;
            while((n=in.read(b))>0) md.update(b,0,n);
        }
        StringBuilder sb=new StringBuilder();
        for(byte x:md.digest()) sb.append(String.format("%02x",x));
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        File dir=new File(args.length>0?args[0]:"."); dir.mkdirs();
        File wud=new File(dir,"sample.wud");
        File wux=new File(dir,"sample.wux");
        File round=new File(dir,"roundtrip.wud");
        final int S=0x8000;
        byte[] a=new byte[S], b=new byte[S], c=new byte[S];
        for(int i=0;i<S;i++) { a[i]=(byte)(i*13+7); b[i]=(byte)(i*31+3); }
        new Random(123456789L).nextBytes(c);
        try(var out=Files.newOutputStream(wud.toPath())) {
            out.write(a); out.write(b); out.write(a); out.write(new byte[S]); out.write(c); out.write(b); out.write(new byte[S]);
        }
        try(Raf in=new Raf(wud,"r"); Raf out=new Raf(wux,"rw")) {
            int d=WudEngine.detect(in);
            if(d!=WudEngine.MODE_WUD_TO_WUX) throw new AssertionError("detect wud "+d);
            int r=WudEngine.process(in,out,true,null);
            if(r!=0) throw new AssertionError("compress "+r);
        }
        try(Raf in=new Raf(wux,"r")) {
            int d=WudEngine.detect(in);
            if(d!=WudEngine.MODE_WUX_TO_WUD) throw new AssertionError("detect wux "+d);
        }
        try(Raf in=new Raf(wux,"r"); Raf out=new Raf(round,"rw")) {
            int r=WudEngine.process(in,out,true,null);
            if(r!=0) throw new AssertionError("decompress "+r);
        }
        if(!Arrays.equals(Files.readAllBytes(wud.toPath()), Files.readAllBytes(round.toPath())))
            throw new AssertionError("roundtrip bytes differ");
        System.out.println("WUD sha256="+sha(wud));
        System.out.println("WUX sha256="+sha(wux));
        System.out.println("ROUND sha256="+sha(round));
        System.out.println("WUD bytes="+wud.length()+" WUX bytes="+wux.length());
        System.out.println("PASS");
    }
}
