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
public class NoData extends Data {
  public NoData() {
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof NoData)) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash("NoData");
  }
  
  @Override
  public String getMime() {
    return "lancopy/nodata";
  }

  @Override
  public String toString() {
    return "[NO DATA]";
  }

  @Override
  public InputStream serialize() {
    return new ByteArrayInputStream(new byte[0]);
  }

  public static NoData deserialize(InputStream stream) {
    return new NoData();
  }
}
