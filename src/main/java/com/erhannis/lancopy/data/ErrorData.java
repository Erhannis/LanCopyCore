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
public class ErrorData extends Data {
  private static final Charset UTF8 = Charset.forName("UTF-8");
  
  public final String text;

  public ErrorData(String text) {
    this.text = text;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof ErrorData)) {
      return false;
    }
    return Objects.equals(this.text, ((ErrorData)obj).text);
  }

  @Override
  public int hashCode() {
    return Objects.hash("ErrorData", text);
  }
  
  @Override
  public String getMime() {
    return "text/error";
  }

  @Override
  public String toString() {
    return "[ERROR] " + text;
  }

  @Override
  public InputStream serialize() {
    return new ByteArrayInputStream(text.getBytes(UTF8));
  }

  public static ErrorData deserialize(InputStream stream) {
    String text = "";
    try {
      text = new String(ByteStreams.toByteArray(stream), UTF8);
    } catch (IOException ex) {
      Logger.getLogger(ErrorData.class.getName()).log(Level.SEVERE, null, ex);
      return new ErrorData("ERROR DESERIALIZING ERROR (HAHA) : " + ex.getMessage());
    }
    return new ErrorData(text);
  }
}
