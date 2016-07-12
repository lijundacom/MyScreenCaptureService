/*
 * Copyright (C) 2011-2014 GUIGUI Simon, fyhertz@gmail.com
 * 
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 * 
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.example.ljd.myscreencaptureservice.rtspserver.rtcp;

import static com.example.ljd.myscreencaptureservice.rtspserver.rtp.RtpSocket.TRANSPORT_TCP;
import static com.example.ljd.myscreencaptureservice.rtspserver.rtp.RtpSocket.TRANSPORT_UDP;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.channels.IllegalSelectorException;

import android.os.SystemClock;
import android.util.Log;

/**
 * Implementation of Sender Report RTCP packets.
 */
/*RTCP：五种数据类型
			200，SR（Sender Report），发送端报告
			201，RR（Receiver Report），接收端报告
			202，SDES（Source Description Items），源点描述
			203，BYE ，结束传输
			204，APP ，特定应用
		 */
public class SenderReport {

	public static final int MTU = 1500;//最大传输单元，所能通过的最大数据包大小（以字节为单位）

	private static final int PACKET_LENGTH = 28;
	
	private MulticastSocket usock;
	private DatagramPacket upack;

	private int mTransport;
	private OutputStream mOutputStream = null;
	private byte[] mBuffer = new byte[MTU];
	private int mSSRC, mPort = -1;
	private int mOctetCount = 0, mPacketCount = 0;
	private long interval, delta, now, oldnow;
	private byte mTcpHeader[];
	private InetAddress myDest;
	public SenderReport(int ssrc) throws IOException {
		super();
		this.mSSRC = ssrc;
	}
	
	public SenderReport() {

		mTransport = TRANSPORT_UDP;
		mTcpHeader = new byte[] {'$',0,0,PACKET_LENGTH};
		
		/*							     Version(2)  Padding(0)					 					*/
		/*									 ^		  ^			PT = 0	    						*/
		/*									 |		  |				^								*/
		/*									 | --------			 	|								*/
		/*									 | |---------------------								*/
		/*									 | ||													*/
		/*									 | ||													*/
		mBuffer[0] = (byte) Integer.parseInt("10000000",2);//表示"10000000"是个二进制的数，要转化为十进制

		/* Packet Type PT */
		mBuffer[1] = (byte) 200;//包类型（PT）：8比特，SR包包含常量200。

		/* Byte 2,3          ->  Length		                     */
		setLong(PACKET_LENGTH/4-1, 2, 4);//长度域（Length）：16比特，其中存放的是该SR包以32比特为单位的总长度减一。

		/* Byte 4,5,6,7      ->  SSRC                            *///同步源（SSRC）：SR包发送者的同步源标识符。与对应RTP包中的SSRC一样。
		/* Byte 8,9,10,11    ->  NTP timestamp hb				 *///NTP Timestamp（Network time protocol）SR包发送时的绝对时间值。NTP的作用是同步不同的RTP媒体流。
		/* Byte 12,13,14,15  ->  NTP timestamp lb				 */
		/* Byte 16,17,18,19  ->  RTP timestamp		             *///RTP Timestamp：与NTP时间戳对应，与RTP数据包中的RTP时间戳具有相同的单位和随机初始值。
		/* Byte 20,21,22,23  ->  packet count				 	 *///Sender’s packet count：从开始发送包到产生这个SR包这段时间里，发送者发送的RTP数据包的总数. SSRC改变时，这个域清零。
		/* Byte 24,25,26,27  ->  octet count			         *///从开始发送包到产生这个SR包这段时间里，发送者发送的净荷数据的总字节数（不包括头部和填充）。发送者改变其SSRC时，这个域要清零。

		try {
			usock = new MulticastSocket();
		} catch (IOException e) {
			// Very unlikely to happen. Means that all UDP ports are already being used
			throw new RuntimeException(e.getMessage());
		}
		upack = new DatagramPacket(mBuffer, 1);

		// By default we sent one report every 3 secconde
		interval = 3000;
		
	}

	public void close() {
		try {
			usock.leaveGroup(myDest);
		} catch (IOException e) {
			e.printStackTrace();
		}
		usock.close();
	}

	/**
	 * Sets the temporal interval between two RTCP Sender Reports.
	 * Default interval is set to 3 seconds.
	 * Set 0 to disable RTCP.
	 * @param interval The interval in milliseconds
	 */
	public void setInterval(long interval) {
		this.interval = interval;
	}	

	/** 
	 * Updates the number of packets sent, and the total amount of data sent.
	 * @param length The length of the packet 
	 * @param rtpts
	 *            The RTP timestamp.
	 * @throws IOException 
	 **/
	public void update(int length, long rtpts) throws IOException {
		mPacketCount += 1;
		mOctetCount += length;
		setLong(mPacketCount, 20, 24);
		setLong(mOctetCount, 24, 28);

		now = SystemClock.elapsedRealtime();
		delta += oldnow != 0 ? now-oldnow : 0;
		oldnow = now;
		if (interval>0) {
			if (delta>=interval) {
				// We send a Sender Report
				send(System.nanoTime(), rtpts);
				delta = 0;
			}
		}
		
	}

	public void setSSRC(int ssrc) {
		this.mSSRC = ssrc; 
		setLong(ssrc,4,8);
		mPacketCount = 0;
		mOctetCount = 0;
		setLong(mPacketCount, 20, 24);
		setLong(mOctetCount, 24, 28);
	}

	public void setDestination(InetAddress dest, int dport) {
		myDest = dest;
		try {
			usock.joinGroup(dest);
		} catch (IOException e) {
			e.printStackTrace();
		}
		mTransport = TRANSPORT_UDP;
		mPort = dport;
		upack.setPort(dport);
		upack.setAddress(dest);
	}

	/**
	 * If a TCP is used as the transport protocol for the RTP session,
	 * the output stream to which RTP packets will be written to must
	 * be specified with this method.
	 */ 
	public void setOutputStream(OutputStream os, byte channelIdentifier) {
		mTransport = TRANSPORT_TCP;
		mOutputStream = os;
		mTcpHeader[1] = channelIdentifier;
	}	
	
	public int getPort() {
		return mPort;
	}

	public int getLocalPort() {
		return usock.getLocalPort();
	}

	public int getSSRC() {
		return mSSRC;
	}

	/**
	 * Resets the reports (total number of bytes sent, number of packets sent, etc.)
	 */
	public void reset() {
		mPacketCount = 0;
		mOctetCount = 0;
		setLong(mPacketCount, 20, 24);
		setLong(mOctetCount, 24, 28);
		delta = now = oldnow = 0;
	}
	
	private void setLong(long n, int begin, int end) {
		for (end--; end >= begin; end--) {
			mBuffer[end] = (byte) (n % 256);
			n >>= 8;
		}
	}	

	/**
	 * Sends the RTCP packet over the network.
	 * 
	 * @param ntpts
	 *            the NTP timestamp.
	 * @param rtpts
	 *            the RTP timestamp.
	 */
	private void send(long ntpts, long rtpts) throws IOException {
		long hb = ntpts/1000000000;
		long lb = ( ( ntpts - hb*1000000000 ) * 4294967296L )/1000000000;
		setLong(hb, 8, 12);
		setLong(lb, 12, 16);
		setLong(rtpts, 16, 20);
		if (mTransport == TRANSPORT_UDP) {
			upack.setLength(PACKET_LENGTH);
			usock.send(upack);		
		} else {
			synchronized (mOutputStream) {
				try {
					mOutputStream.write(mTcpHeader);
					mOutputStream.write(mBuffer, 0, PACKET_LENGTH);
				} catch (Exception e) {}
			}
		}
	}
		
	
}
