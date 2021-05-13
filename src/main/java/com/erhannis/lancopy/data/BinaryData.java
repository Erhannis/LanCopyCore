/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.data;

import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author erhannis
 */
public class BinaryData extends Data {
  private static final Charset UTF8 = Charset.forName("UTF-8");
  
  public final InputStream stream;

  public BinaryData(InputStream stream) {
    this.stream = stream;
  }
  
  @Override
  public String getMime() {
    return "application/octet-stream";
  }

  @Override
  public String toString() {
    return "[binary]"; //TODO Length would be nice, but we may not always have
  }

  /**
   * Warning: this method returns the raw Stream, and may therefore be destructive.
   * @return 
   */
  @Override
  public InputStream serialize() {
    return stream; //TODO Warning: this can maybe only be used once per BinaryData
  }

  public static Data deserialize(InputStream stream) {
    return new BinaryData(stream);
  }
}
