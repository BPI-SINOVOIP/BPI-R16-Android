package com.allwinnertech.smartlink;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by A on 2015/10/19.
 */
public class AirkissUdpSend extends TimerTask {
    private String ip;
    private int port;
    private int interval;
    private int random;
    private int times = 0;
    private Timer timer;
    private AirkissUdpSendListener mListener = null;

    public interface AirkissUdpSendListener{
        void onSendFinished();
    }
    public AirkissUdpSend(String ip,int port,int interval,int random){
        this.ip = ip;
        this.port = port;

        this.interval = interval;
        this.random = random;

        timer = new Timer();

    }
    public void setListener(AirkissUdpSendListener l){
        mListener = l;
    }
    public void start()
    {
        timer.schedule(this,100,interval);
    }

    public void sendData() throws SocketException,
            UnknownHostException {
        DatagramSocket ds = new DatagramSocket();//

        byte[] b = new byte[1];
        b[0] = (byte)random;
        DatagramPacket dp = new DatagramPacket(b,
                b.length,
                InetAddress.getByName(ip), port);
        // 构造数据报包，用来将长度为 length 的包发送到指定主机上的指定端口号
        try {
            ds.send(dp);
            if(times++ > 50) {
                if(mListener != null) mListener.onSendFinished();
                timer.cancel();
            }
        } catch (IOException e) {
            //e.printStackTrace();
        }
        ds.close();

    }
    @Override
    public void run() {
        try {
            sendData();
        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}