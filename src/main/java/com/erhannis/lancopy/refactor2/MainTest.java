/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2;

import com.erhannis.lancopy.DataOwner;

/**
 *
 * @author erhannis
 */
public class MainTest {
    public static void main(String[] args) {
    }
    
    private static void start() {
        DataOwner dataOwner = new DataOwner();
        NodeManager nm = new NodeManager(dataOwner, nodeId, txMsgIn, rxMsgOut, shuffleChannelIn, channelReaderShuffleOut, incomingConnectionIn, subscribeIn, commStatusOut);
    }
}
