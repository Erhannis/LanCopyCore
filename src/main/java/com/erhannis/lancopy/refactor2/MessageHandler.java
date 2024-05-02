package com.erhannis.lancopy.refactor2;

import com.erhannis.lancopy.refactor2.NodeManager;
import com.erhannis.mathnstuff.Pair;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author erhannis
 */
public interface MessageHandler {
    /**
     * Handle message.  Return false if the message should be passed to another
     * handler, e.g. if this handler cannot handle the message.
     * @param msg
     * @return 
     */
    public boolean handleMessage(Pair<NodeManager.CRToken, Object> msg);
}
