/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package com.erhannis.lancopy.refactor2.qr;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.mathnstuff.components.ImagePanel;
import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import jcsp.helpers.JcspUtils;
import jcsp.helpers.NameParallel;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.CSProcess;
import jcsp.lang.CSTimer;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
import jcsp.lang.Guard;
import jcsp.lang.ProcessManager;
import jcsp.util.InfiniteBuffer;
import jcsp.util.OverWriteOldestBuffer;

/**
 *
 * @author erhannis
 */
public class QRCommFrame extends javax.swing.JFrame {

    public final QRCommChannel channel;

    /**
     * Creates new form QRCommFrame
     */
    public QRCommFrame(DataOwner dataOwner, QRComm comm) {
        initComponents();

        Webcam webcam = Webcam.getDefault();
        Dimension[] ds = webcam.getViewSizes();
        webcam.setViewSize(ds[ds.length - 1]);
        webcam.open();
        jSplitPane2.setRightComponent(new WebcamPanel(webcam));


        //DO Resize/scale
        final ImagePanel qrImagePanel = new ImagePanel();
        jSplitPane1.setDividerLocation(100);
        jSplitPane1.setRightComponent(qrImagePanel);
        qrImagePanel.invalidate();

        
        Any2OneChannel<byte[]> txBytesChannel = Channel.<byte[]>any2one(5);
        AltingChannelInput<byte[]> txBytesIn = txBytesChannel.in();
        ChannelOutput<byte[]> txBytesOut = JcspUtils.logDeadlock(txBytesChannel.out());

        // The buffering is a little non-standard, but I think this is right
        
        Any2OneChannel<byte[]> rxBytesChannel = Channel.<byte[]>any2one(new InfiniteBuffer<>(), 5);
        AltingChannelInput<byte[]> rxBytesIn = rxBytesChannel.in();
        ChannelOutput<byte[]> rxBytesOut = JcspUtils.logDeadlock(rxBytesChannel.out());

        Any2OneChannel<byte[]> txQrChannel = Channel.<byte[]>any2one(new InfiniteBuffer<>(), 5);
        AltingChannelInput<byte[]> txQrIn = txQrChannel.in();
        ChannelOutput<byte[]> txQrOut = JcspUtils.logDeadlock(txQrChannel.out());

        Any2OneChannel<byte[]> rxQrChannel = Channel.<byte[]>any2one(5);
        AltingChannelInput<byte[]> rxQrIn = rxQrChannel.in();
        ChannelOutput<byte[]> rxQrOut = JcspUtils.logDeadlock(rxQrChannel.out());

        Any2OneChannel<String> statusChannel = Channel.<String> any2one(new OverWriteOldestBuffer<>(1), 5);
        AltingChannelInput<String> statusIn = statusChannel.in();
        ChannelOutput<String> statusOut = JcspUtils.logDeadlock(statusChannel.out());
        
        
        this.channel = new QRCommChannel(dataOwner, txBytesOut, rxBytesIn, comm);
        new ProcessManager(new NameParallel(new CSProcess[]{
            new QRProcess(txBytesIn, txQrOut, rxBytesOut, rxQrIn, statusOut),
            
            () -> { // Camera loop
                CSTimer timer = new CSTimer();
                while (true) {
                    if (!webcam.isOpen()) {
                        timer.sleep(200);
                        continue;
                    }
                    BufferedImage image = webcam.getImage();
                    if (image == null) {
                        continue;
                    }

                    Result result = null;
                    try {
                        result = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))));
                    } catch (NotFoundException e) {
                        continue;
                    } catch (ChecksumException ex) {
                        continue;
                    } catch (FormatException ex) {
                        continue;
                    }

                    if (result != null) {
                        ArrayList<byte[]> segments = (ArrayList<byte[]>) result.getResultMetadata().get(ResultMetadataType.BYTE_SEGMENTS);
                        if (segments.size() > 1) {
                            System.err.println("Got more than one segment in a qr code!!! What does it mean?!?");
                        }
                        for (byte[] segment : segments) {
                            rxQrOut.write(segment);
                        }
                    }
                }
            },
            
            () -> { // Handler loop
                Alternative alt = new Alternative(new Guard[]{txQrIn, statusIn});
                while (true) {
                    switch (alt.priSelect()) {
                        case 0: { // txQrIn
                            byte[] qr = txQrIn.read();
                            try {
                                BufferedImage bi = getQR(qr);
                                qrImagePanel.setImage(bi);
                            } catch (WriterException ex) {
                                Logger.getLogger(QRCommFrame.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            break;
                        }
                        case 1: { // statusIn
                            String status = statusIn.read();
                            labelStatus.setText(status);
                            break;
                        }
                    }
                }
            }
        })).start();
        
        this.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                webcam.close();
                rxQrOut.poison(10);
                txQrIn.poison(10);
            }
        });        
    }

    private static final Charset CHARSET = Charset.forName("ISO_8859-1");
    
    private static BufferedImage getQR(byte[] data) throws WriterException {
        BitMatrix matrix = new MultiFormatWriter().encode(new String(data, CHARSET), BarcodeFormat.QR_CODE, 700, 700);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jSplitPane1 = new javax.swing.JSplitPane();
        jSplitPane2 = new javax.swing.JSplitPane();
        labelStatus = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        jSplitPane1.setDividerLocation(100);
        jSplitPane1.setResizeWeight(1.0);

        jSplitPane2.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        labelStatus.setText("jLabel1");
        jSplitPane2.setTopComponent(labelStatus);

        jSplitPane1.setLeftComponent(jSplitPane2);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 841, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 709, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JSplitPane jSplitPane2;
    private javax.swing.JLabel labelStatus;
    // End of variables declaration//GEN-END:variables
}
