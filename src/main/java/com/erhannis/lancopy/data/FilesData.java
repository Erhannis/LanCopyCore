/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.data;

import com.erhannis.mathnstuff.MeUtils;
import com.erhannis.mathnstuff.utils.Timing;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import org.apache.commons.io.FileUtils;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarOutputStream;

/**
 *
 * @author erhannis
 */
public class FilesData extends Data {
  private static final Charset UTF8 = Charset.forName("UTF-8");

  public final File[] files;

  public FilesData(File[] files) {
    this.files = files;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof FilesData)) {
      return false;
    }
    return Objects.deepEquals(this.files, ((FilesData)obj).files);
  }

  @Override
  public int hashCode() {
    return Objects.hash("FilesData", Arrays.hashCode(files));
  }
  
  @Override
  public String getMime(boolean external) {
    if (external) {
      return "application/octet-stream";
    } else {
      return "lancopy/files";
    }
  }

  @Override
  public String toString() {
    String[] subtexts = new String[files.length];
    for (int i = 0; i < files.length; i++) {
      subtexts[i] = files[i].getName() + " (" + files[i].length() + ")";
    }
    return "[files] {" + String.join(", ", subtexts) + "}";
  }

  public String toLongString() {
    String[] subtexts = new String[files.length+1];
    subtexts[0] = "[files]";
    if (files.length > 0) {
      try {
        subtexts[0] = "[files]\n"+files[0].getParentFile().getAbsolutePath()+"\n----";
      } catch (Throwable t) {
        // Nevermind
      }
    }
    for (int i = 0; i < files.length; i++) {
      subtexts[i+1] = files[i] + " (" + files[i].length() + ")";
    }
    return String.join("\n", subtexts);
  }
  
  private static class PathedFile {
    public final String path;
    public final File file;

    public PathedFile(String path, File file) {
      this.path = path;
      this.file = file;
    }
  }

  @Override
  public InputStream serialize(boolean external) {
    System.out.println("--> FilesData serialize");
    Timing timing = new Timing();
    try {
    if (files.length == 1 && !files[0].isDirectory()) {
      try {
        // This is a little cluttered
        byte[] filenameBytes = files[0].getName().getBytes(UTF8);
        if (external) {
          return new FileInputStream(files[0]);
        } else {
          return new SequenceInputStream(new ByteArrayInputStream(Bytes.concat(
                  Ints.toByteArray(1), // Not yet really used
                  Ints.toByteArray(filenameBytes.length),
                  filenameBytes)),
                  new FileInputStream(files[0]));
        }
      } catch (FileNotFoundException ex) {
        Logger.getLogger(FilesData.class.getName()).log(Level.SEVERE, null, ex);
        return new ErrorData("Error serializing files: " + ex.getMessage()).serialize(external); //TODO Wrong mime type
      }
    }

    return MeUtils.incrementalStream(os -> {
        try {
            Date date = new Date();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            byte[] filenameBytes = (dateFormat.format(date)+".tar").getBytes(UTF8);
            if (!external) {
                MeUtils.pipeInputStreamToOutputStream(new ByteArrayInputStream(Bytes.concat(
                  Ints.toByteArray(1),
                  Ints.toByteArray(filenameBytes.length),
                  filenameBytes)), os);
            }
            
            LinkedList<PathedFile> pending = new LinkedList<>();
            for (File f : files) {
              pending.add(new PathedFile("", f));
            }

            try (TarOutputStream out = new TarOutputStream(os)) {
              while (!pending.isEmpty()) {
                PathedFile pf = pending.pop();
                if (pf.file.isDirectory()) {
                  for (File f : pf.file.listFiles()) {
                    PathedFile subfile = new PathedFile(pf.path + "/" + pf.file.getName(), f);
                    pending.add(subfile);
                  }
                  continue;
                }
                out.putNextEntry(new TarEntry(pf.file, pf.path + "/" + pf.file.getName()));
                BufferedInputStream origin = new BufferedInputStream(new FileInputStream(pf.file));
                int count;
                byte data[] = new byte[2048];

                while ((count = origin.read(data)) != -1) {
                  out.write(data, 0, count);
                }

                out.flush();
                origin.close();
              }
            } catch (IOException ex) {
              Logger.getLogger(FilesData.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (IOException e) {
            //TODO Not really an error; probably don't even need to log the trace
            System.err.println("FilesData serialize stream aborted");
            e.printStackTrace();
        }
    });

    } finally {
        System.out.println("<-- FilesData serialize "+timing.stop());
    }
  }

  public static JFileChooser fileChooser = new JFileChooser();
  
  public synchronized static Data deserialize(InputStream stream) {
    try {
      int fileCount = Ints.fromByteArray(MeUtils.readNBytes(stream, 4));
      File[] files = new File[fileCount];
      for (int i = 0; i < fileCount; i++) {
        int filenameLen = Ints.fromByteArray(MeUtils.readNBytes(stream, 4));
        String filename = new String(MeUtils.readNBytes(stream, filenameLen), UTF8);
        
        //TODO Huh.  Should I really bring up a save gui in the middle of this code?
        File f = new File(filename);
        fileChooser.setSelectedFile(f);
        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
          f = fileChooser.getSelectedFile();
        } else {
          throw new RuntimeException("File save canceled");
        }
//        if (f.exists()) {
//          throw new IllegalStateException("File already exists! " + filename);
//        }
        //TODO Wait until all files named?
        FileUtils.copyInputStreamToFile(stream, f);
        files[i] = f;
      }
      System.out.println("Done deserializing files");
      System.out.println("<-- FilesData deserialize");
      return new FilesData(files);
    } catch (Throwable t) {
      Logger.getLogger(FilesData.class.getName()).log(Level.SEVERE, null, t);
      System.out.println("<-- FilesData deserialize");
      return new ErrorData("Error deserializing files: " + t);
    }
  }
}
