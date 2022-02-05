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
import java.awt.Graphics2D;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
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
        jSplitPane1.setLeftComponent(new WebcamPanel(webcam));
        jSplitPane1.setDividerLocation(100);


        //DO Resize/scale
        final ImagePanel qrImagePanel = new ImagePanel();
        jSplitPane2.setRightComponent(qrImagePanel);
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
        
        
        String[] lastStatus = {"None"};
        
        this.channel = new QRCommChannel(dataOwner, txBytesOut, rxBytesIn, comm);
        new ProcessManager(new NameParallel(new CSProcess[]{
            new QRProcess(txBytesIn, txQrOut, rxBytesOut, rxQrIn, statusOut),
            
            () -> {
                Thread.currentThread().setName("QR Camera loop");
                CSTimer timer = new CSTimer();
                while (true) {
                    if (!webcam.isOpen()) {
                        timer.sleep(200);
                        taStatus.setText(lastStatus[0] + "\n" + "camera not open");
                        continue;
                    }
                    BufferedImage image = webcam.getImage();
                    if (image == null) {
                        taStatus.setText(lastStatus[0] + "\n" + "no image");
                        continue;
                    }

                    Result result = null;
                    try {
                        result = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))));
                        taStatus.setText(lastStatus[0] + "\n" + "qr visible");
                    } catch (NotFoundException e) {
                        taStatus.setText(lastStatus[0] + "\n" + "qr not visible 1");
                        continue;
                    } catch (ChecksumException ex) {
                        taStatus.setText(lastStatus[0] + "\n" + "qr not visible 2");
                        continue;
                    } catch (FormatException ex) {
                        taStatus.setText(lastStatus[0] + "\n" + "qr not visible 3");
                        continue;
                    }

                    if (result != null) {
                        ArrayList<byte[]> segments = (ArrayList<byte[]>) result.getResultMetadata().get(ResultMetadataType.BYTE_SEGMENTS);
                        if (segments.size() > 1) {
                            System.err.println("Got more than one segment in a qr code!!! What does it mean?!?");
                        }
                        for (byte[] segment : segments) {
                            System.out.println("QR <-RXb " + Arrays.toString(segment));
                            System.out.println("QR <-RXc " + new String(segment, CHARSET));
                            
                            rxQrOut.write(segment);
                        }
                    }
                }
            },
            
            () -> {
                Thread.currentThread().setName("QR handler loop");
                Random r = new Random();
                CSTimer jitterTimer = new CSTimer();
                // The jitter is because sometimes the reader would get stuck, and slightly moving the image unsticks it
                jitterTimer.setAlarm(jitterTimer.read()+((Long) dataOwner.options.getOrDefault("Comms.qr.JITTER.INTERVAL",200L)));
                BufferedImage lastTxQr = null;
                
                Alternative alt = new Alternative(new Guard[]{txQrIn, statusIn, jitterTimer});
                while (true) {
                    switch (alt.priSelect()) {
                        case 0: { // txQrIn
                            byte[] qr = txQrIn.read();
                            System.out.println("QR TXb-> " + Arrays.toString(qr));
                            System.out.println("QR TXc-> " + new String(qr, CHARSET));
                            try {
                                BufferedImage bi = getQR(qr);
                                qrImagePanel.setImage(bi);
                                lastTxQr = bi;
                                long INTERVAL = (Long) dataOwner.options.getOrDefault("Comms.qr.JITTER.INTERVAL",200L);
                                jitterTimer.setAlarm(jitterTimer.read()+INTERVAL);
                            } catch (WriterException ex) {
                                Logger.getLogger(QRCommFrame.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            break;
                        }
                        case 1: { // statusIn
                            lastStatus[0] = statusIn.read();
                            break;
                        }
                        case 2: { // jitterTimer
                            if (lastTxQr != null) {
                                double X = (Double) dataOwner.options.getOrDefault("Comms.qr.JITTER.X",100.0);
                                double Y = (Double) dataOwner.options.getOrDefault("Comms.qr.JITTER.Y",100.0);
                                boolean ROTATE = (Boolean) dataOwner.options.getOrDefault("Comms.qr.JITTER.ROTATE",true);
                                BufferedImage jittered = transform(lastTxQr, ROTATE ? r.nextInt(4) * Math.PI / 2 : 0, (r.nextDouble()*X)-(X/2), (r.nextDouble()*Y)-(Y/2));
                                qrImagePanel.setImage(jittered);
                            }
                            long INTERVAL = (Long) dataOwner.options.getOrDefault("Comms.qr.JITTER.INTERVAL",200L);
                            jitterTimer.setAlarm(jitterTimer.read()+INTERVAL);
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

    // https://stackoverflow.com/a/52663539/513038
    public static BufferedImage transform(BufferedImage src, double angle, double tx, double ty) {
        int width = src.getWidth();
        int height = src.getHeight();

        BufferedImage dest = new BufferedImage(height, width, src.getType());

        Graphics2D graphics2D = dest.createGraphics();
        graphics2D.translate((height - width) / 2, (height - width) / 2);
        graphics2D.rotate(angle, height / 2, width / 2);
        graphics2D.translate(tx, ty);
        graphics2D.drawRenderedImage(src, null);

        return dest;
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
        jScrollPane1 = new javax.swing.JScrollPane();
        taStatus = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        jSplitPane1.setDividerLocation(100);
        jSplitPane1.setResizeWeight(1.0);

        jSplitPane2.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        taStatus.setColumns(20);
        taStatus.setRows(5);
        jScrollPane1.setViewportView(taStatus);

        jSplitPane2.setTopComponent(jScrollPane1);

        jSplitPane1.setRightComponent(jSplitPane2);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 1108, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 768, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JSplitPane jSplitPane2;
    private javax.swing.JTextArea taStatus;
    // End of variables declaration//GEN-END:variables
}
