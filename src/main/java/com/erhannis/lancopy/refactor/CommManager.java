/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor;

import java.util.List;

/**
 *
 * @author erhannis
 */
public interface CommManager {
    public List<Comm> getComms();
    public void startup();
    public void shutdown();
}
