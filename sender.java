import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Sender application
 * The sender implements Go-Back-N behaviour.
 * It provides reliable data transfer of a file to the receiver via the provided
 * network emulator
 */
public class sender{
  InetAddress host;
  int emuPort, ackPort;
  BufferedReader br;
  PrintWriter seqOut, ackOut;
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
      seqOut = new PrintWriter("seqNum.log");
      ackOut = new PrintWriter("ack.log");
      sendSocket = new DatagramSocket();
      receiveSocket = new DatagramSocket(ackPort);

      windowSize = 10;
      // packets buffer to keep track of sent but unacked packets
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
      e.printStackTrace();
      System.exit(1);
    }

  }
  
  /**
   * Sends packets of size 500 chars until EOF is reached
   */
  private void transmit() throws Exception{

    while(!done){
      // read 500 chars
      char[] buf = new char[500];
      int read = br.read(buf, 0, 500);
      if(read < 500){
        // copy buffer to smaller sized buffer to avoid sending unnessessary data
        done = true;
        char[] tmp = new char[read];
        System.arraycopy(buf, 0, tmp, 0, read);
        buf = tmp;
      }
      packet p = packet.createPacket(i, new String(buf));
      curPacket = p;

      // if window is full, wait a while and try sending again
      boolean sent = false;
      while(!sent) {
        if (packets.size() < windowSize){
          sendPackets(false);
          sent = true;
        } else {
          Thread.sleep(500);
        }
      }

      // add the packet just sent to packets buffer
      synchronized(packets){
        packets.add(p);
      }
      // start timer if not already started
      if(!scheduled){
        scheduleTask();
      }
      i++;
      i%=32;
    }
  }

  /**
   * Listens for incoming packets until all packets and EOT have been sent
   */
  private void listen() throws Exception{
    byte[] receiveData = new byte[512];

    // loop until all packets are sent
    while(!done || !packets.isEmpty()){
      // get ack
      DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
      receiveSocket.receive(receivePacket);
      packet ack = packet.parseUDPdata(receivePacket.getData());
      // discard if it's not an ack
      if(ack.getType() == 0){
        // output to ack.log
        ackOut.println(ack.getSeqNum());
        synchronized(packets){
          packet ackedP = null;
          // find the packet the ack matches to
          for(packet p: packets){
            if(ack.getSeqNum() == p.getSeqNum())
              ackedP = p;
          }
          // ack is cumulative
          // all packets before the matched packet have been received, so remove them
          if(ackedP != null){
            packet p = packets.removeFirst();
            while(p != ackedP)
              p = packets.removeFirst();
          }
        }

        // if there is still outstanding unacked packets, restart timer
        // otherwise stop timer
        if(!packets.isEmpty()){
          scheduleTask();
        } else {
          cancelTask();
        }
      }
    }

  }

  // restarts the timer
  private void scheduleTask(){
    synchronized(task){
      task.cancel();
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
      timer.schedule(task, 300);
      scheduled = true;
    }
  }

  // stops the timer
  private void cancelTask(){
    synchronized(task){
      task.cancel();
      scheduled = false;
    }
  }

  // if timer expire, send all outstanding unacked packets
  // then restart timer
  private void timerExpired() throws Exception{
    sendPackets(true);
    scheduleTask();
  }

  /**
   * Sends a single packet or sends all unacked packets
   */
  private void sendPackets(boolean sendAll) throws Exception{
    byte[] sendData = new byte[512];
    synchronized(packets){
      // if sendAll flag is set, send all unacked packets in the buffer
      if(sendAll){
        for (packet p: packets){
          sendData = p.getUDPdata();
          DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
              host, emuPort);
          sendSocket.send(sendPacket);
          seqOut.println(p.getSeqNum());
        }
      }else{
        // otherwise send the current packet
        sendData = curPacket.getUDPdata();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
            host, emuPort);
        sendSocket.send(sendPacket);
        seqOut.println(curPacket.getSeqNum());
      }
    }
  }

  /**
   * Called from main to start the sender process
   */
  public void start() throws Exception{
    
    // create another thread to handle incoming acks
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

    // use the current thread to send data
    transmit();

    // transmit() finished, meaning the file has been read and all packets have been sent
    // now wait until all packets are acked
    while(!packets.isEmpty()){
      Thread.sleep(500);
    }

    // all packets are sent, and acks received
    // now cancel the timer
    timer.cancel();
    scheduled = false;

    // send EOT
    byte[] sendData = packet.createEOT(i).getUDPdata();
    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
        host, emuPort);
    sendSocket.send(sendPacket);

    br.close();
    seqOut.close();
    ackOut.close();
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
