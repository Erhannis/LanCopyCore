/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.data;

import java.io.InputStream;

/**
 *
 * @author erhannis
 */
public abstract class Data {
  public abstract String getMime();
  public abstract InputStream serialize();
  // Subclasses are expected to have a static deserialize method, as below:
  //public static abstract Data deserialize(InputStream stream);
}
