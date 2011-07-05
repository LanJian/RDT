import java.io.*;
import java.net.*;
import java.util.*;

public class sender{
  InetAddress host;
  int emuPort, ackPort;
  BufferedReader br;
  DatagramSocket sendSocket;
  DatagramSocket receiveSocket;
  Timer timer;
  TimerTask task;
  boolean scheduled;

  int windowSize;
  LinkedList<packet> packets;
  packet curPacket;
  int i;
  boolean done;

  public sender(String h, String ep, String ap, String f){
    try {
      host = InetAddress.getByName(h);
      emuPort = Integer.parseInt(ep);
      ackPort = Integer.parseInt(ap);
      br = new BufferedReader(new FileReader(f));
      sendSocket = new DatagramSocket();
      receiveSocket = new DatagramSocket(ackPort);

      windowSize = 10;
      packets = new LinkedList<packet>();
      curPacket = null;
      i = 0;
      done = false;

      timer = new Timer();
      task = new TimerTask(){
        public void run(){
          try{
            timerExpired();
          }catch(Exception e){
            System.err.println("An error occured - create task");
            e.printStackTrace();
            System.exit(1);
          }
        }
      };
      scheduled = false;
    } catch (UnknownHostException e) {
      System.err.println("Don't know about host: " + h);
      System.exit(1);
    } catch(Exception e){
      //help text
      e.printStackTrace();
      System.exit(1);
    }

  }
  
  private void transmit() throws Exception{

    while(!done){
      // transmit
      char[] buf = new char[500];
      if(br.read(buf, 0, 500)<500)
        done = true;
      packet p = packet.createPacket(i, new String(buf));
      curPacket = p;

      boolean sent = false;
      while(!sent) {
        if (packets.size() < windowSize){
          sendPackets(false);
          sent = true;
        } else {
          Thread.sleep(500);
        }
      }

      synchronized(packets){
        packets.add(p);
      }
      //TODO start timer if not already started
      if(!scheduled){
        createTask();
        timer.schedule(task, 100);
      }
      i++;
      i%=32;
    }
  }

  private void listen() throws Exception{
    byte[] receiveData = new byte[512];

    while(!done || !packets.isEmpty()){
      // get ack
      DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
      receiveSocket.receive(receivePacket);
      packet ack = packet.parseUDPdata(receivePacket.getData());
      if(ack.getType() == 0){
        System.out.println("Got ack: " + ack.getSeqNum());
        packet p = packets.peek();
        while(p!= null && p.getSeqNum() != ack.getSeqNum()){
          synchronized(packets){
            packets.removeFirst();
            p = packets.peek();
          }
        }

        if(!packets.isEmpty()){
          //TODO start timer
          task.cancel();
          createTask();
          timer.schedule(task, 100);
        } else {
          //TODO stop timer
          task.cancel();
          scheduled = false;
        }
      }
    }

  }

  private void createTask(){
    synchronized(task){
      task = new TimerTask(){
        public void run(){
          try{
            timerExpired();
          }catch(Exception e){
            System.err.println("An error occured - create task");
            e.printStackTrace();
            System.exit(1);
          }
        }
      };
    }
  }

  private void timerExpired() throws Exception{
    sendPackets(true);
    //TODO restart timer
    task.cancel();
    createTask();
    timer.schedule(task, 100);
  }

  private void sendPackets(boolean sendAll) throws Exception{
    byte[] sendData = new byte[512];
    synchronized(packets){
      if(sendAll){
        for (packet p: packets){
          sendData = p.getUDPdata();
          DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
              host, emuPort);
          sendSocket.send(sendPacket);
        }
      }else{
        sendData = curPacket.getUDPdata();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
            host, emuPort);
        sendSocket.send(sendPacket);
      }
    }
  }

  public void start() throws Exception{
    
    Thread listener = new Thread(){
      public void run(){
        try{
          listen();
        }catch(Exception e){
          System.err.println("An error occured - listener thread");
          e.printStackTrace();
          System.exit(1);
        }
      }
    };
    listener.start();

    transmit();

    // send EOT
    byte[] sendData = packet.createEOT(i).getUDPdata();
    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
        host, emuPort);
    sendSocket.send(sendPacket);

    while(!packets.isEmpty()){
      Thread.sleep(500);
    }

    task.cancel();
    scheduled = false;
    br.close();
    sendSocket.close();
    receiveSocket.close();
  }

  public static void main(String[] args){
    try{
      sender s = new sender(args[0], args[1], args[2], args[3]);
      s.start();
    }catch(Exception e){
      System.err.println("An error occured");
      e.printStackTrace();
      System.exit(1);
    }
  }
}
