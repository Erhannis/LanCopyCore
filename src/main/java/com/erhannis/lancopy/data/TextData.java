/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.data;

import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author erhannis
 */
public class TextData extends Data {
  private static final Charset UTF8 = Charset.forName("UTF-8");
  
  public final String text;

  public TextData(String text) {
    this.text = text;
  }
  
  @Override
  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof TextData)) {
      return false;
    }
    return Objects.equals(this.text, ((TextData)obj).text);
  }

  @Override
  public int hashCode() {
    return Objects.hash("TextData", text);
  }
  
  @Override
  public String getMime() {
    return "text/plain";
  }

  @Override
  public String toString() {
    return "[text] " + text;
  }

  @Override
  public InputStream serialize() {
    return new ByteArrayInputStream(text.getBytes(UTF8));
  }

  public static Data deserialize(InputStream stream) {
    String text = "";
    try {
      text = new String(ByteStreams.toByteArray(stream), UTF8);
    } catch (IOException ex) {
      Logger.getLogger(ErrorData.class.getName()).log(Level.SEVERE, null, ex);
      return new ErrorData("Error deserializing text: " + ex.getMessage());
    }
    return new TextData(text);
  }
}
