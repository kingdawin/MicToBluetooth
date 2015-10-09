package com.homni.audiobluetooth;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.UUID;

import com.android.webrtc.audio.MobileAEC;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

/**
 * 
 * 录音播放，只要连上蓝牙就能在蓝牙设备播放声音
 * 
 * @author admin
 *
 *         <p>
 *         假设: 通过套接字（Socket）实现了音频数据的网络传输， 做到了一端使用AudioRecord获取音频流然后通过套接字传输出去，
 *         而另一端通过套接字接收后使用AudioTrack类播放
 *         <p>
 *
 *         将存放录音数据的数组放到LinkedList中, 当LinkedList中数组个数达到2（这个也是经过试验验证话音质量最好时的数据）时，
 *         将先录好的数组中数据传送出去。经过上述反复试验和修改， 最终使双方通话质量较好，且延时较短（大概有2秒钟）。
 *         <p>
 *         参考资料：
 *         =========================
 *         1Android中的Audio播放：控制Audio输出通道切换 .
 *         http://blog.csdn.net/l627859442/article/details/7918597
 *         
 *         录音采集源地址
 *         http://blog.csdn.net/hellogv/article/details/6026455
 *         
 *         =========================
 */
public class MainActivity extends Activity
{
	private static final String TAG = "MainActivity";
	 
	private AudioManager mAudioManager;
	
	private Button btnRecord, btnExit;
	// 缓冲区字节大小
	private int recordBuffSize, playBuffSize;
	private boolean isRecording;
	/** 是否连接蓝牙 */
	private boolean isConnectBluetooth;

	// 采集音频的api是android.media.AudioRecord类
	// 在Android中录音可以用MediaRecord录音，操作比较简单。
	// 但是不够专业，就是不能对音频进行处理。如果要进行音频的实时的处理或者音频的一些封装
	// 就可以用AudioRecord来进行录音
	private AudioRecord mAudioRecord;
	/** 播放音频，用write方法 */
	private AudioTrack mAudioTrack;

	// 音频获取源 :麦克风
	private int audioSource = MediaRecorder.AudioSource.MIC;
	// 44.1K
	private int sampleRateInHz = 44100;
	// 设置音频的录制的声道CHANNEL_IN_STEREO为双声道，CHANNEL_CONFIGURATION_MONO为单声道
	private int channelConfig = AudioFormat./*CHANNEL_IN_MONO*/CHANNEL_IN_STEREO;
	// 音频数据格式:PCM 16位每个样本。保证设备支持。PCM 8位每个样本。不一定能得到设备支持。
	private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

	private Receiver mReceiver;
	private IntentFilter intentFilter;

	// 代表了一个本地的蓝牙适配器。他是所有蓝牙交互的的入口点。
	// 利用它你可以发现其他蓝牙设备，查询绑定了的设备，使用已知的MAC地址实例化一个蓝牙设备和建立一个BluetoothServerSocket（作为服务器端）来监听来自其他设备的连接。
	private BluetoothAdapter mAdapter = null;
	private Toast tos;

	//private BluetoothHeadset headset;
	private BluetoothDevice Device;
	private BluetoothA2dp a2dp;
	/** 蓝牙设备名包含的单词 */
	private static final String DEVICE_NAME_CONTAIN = "Live";
	private static final boolean AECM_DEBUG = true;
	private MobileAEC aecm;
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initView();
		
		initAudio();
		bluetoothStatus();
		registerReceiver();
		/*if(isDeviceSupport())
		{
			 initAEC(audioSession);
		}
		*/
		doAECM();
		
	}


	
	/*声音：
	 * 采样率
	 * 比特位
	 * sample
	 * 声道
	 * 
	 * 
	 * */
	// //////////////////////////////////////////////////////////////////////////
	// ACOUSTIC ECHO CANCELLATION MOBILE EDITION

	public void doAECM()
	{
		try
		{
			/*MobileAEC*/ aecm = new MobileAEC(null);
			aecm.setAecmMode(MobileAEC.AggressiveMode.MOST_AGGRESSIVE)
					.prepare();

			// get pcm raw data file in root of android sd card.
			// if you test this demo, you should put these files into your
			// android device or emulator.
			// the ideal output of pcm is almost down to zero.
			//将文件放到手机SD卡根目录
			//需要添加读取SD卡权限
			FileInputStream fin = new FileInputStream(new File(
					Environment.getExternalStorageDirectory()
							+ "/en-00-raw-pcm-16000Hz-16bit-mono.pcm"));

			FileOutputStream fout = new FileOutputStream(new File(
					Environment.getExternalStorageDirectory()
							+ "/aecm-output-pcm-16000Hz-16bit-mono.pcm"));

			final int cacheSize = 320;
			byte[] pcmInputCache = new byte[cacheSize];

			// core procession
			//将文件数据流读到缓冲区pcmInputCache
			for (/* empty */; fin.read(pcmInputCache, 0, pcmInputCache.length) != -1; /* empty */)
			{
				// convert bytes[] to shorts[], and make it into little endian.
				short[] aecTmpIn = new short[cacheSize / 2];
				short[] aecTmpOut = new short[cacheSize / 2];
				//通过包装的方法创建的缓冲区保留了被包装数组内保存的数据
				ByteBuffer.wrap(pcmInputCache)
				//排序
				          .order(ByteOrder.LITTLE_ENDIAN)
				//转成短整形缓冲区,放到    aecTmpIn     
						  .asShortBuffer().get(aecTmpIn);
				//回音处理
				// aecm procession, for now the echo tail is hard-coded 10ms,
				// but you
				// should estimate it correctly each time you call
				// echoCancellation, otherwise aecm
				// cannot work.
				aecm.farendBuffer(aecTmpIn, cacheSize / 2);
				aecm.echoCancellation(aecTmpIn, null, aecTmpOut,
						(short) (cacheSize / 2), (short) 10);

				// output 回声消除之后的buffer
				byte[] aecBuf = new byte[cacheSize];
				ByteBuffer.wrap(aecBuf).order(ByteOrder.LITTLE_ENDIAN)
						.asShortBuffer().put(aecTmpOut);
				fout.write(aecBuf);
			}
			Log.i(TAG, "完成AEC处理");
			fout.close();
			fin.close();
			aecm.close();
		} catch (Exception e)
		{
			Log.e(TAG, "错误 :" + e.getMessage());
			//e.printStackTrace();
		}
	}
	
	public void initView()
	{
		tos = Toast.makeText(this, "", 0);
		btnRecord = (Button) this.findViewById(R.id.btnRecord);
		btnExit = (Button) this.findViewById(R.id.btnExit);
		btnRecord.setOnClickListener(new ClickEvent());
		btnExit.setOnClickListener(new ClickEvent());
	}

	/**
	 * =========================== 
	 * 回声处理
	 * 
	 * ===========================
	 */

	/**
	 * 判断当前机型是否支持AEC，需要注意这里的检查不一定准确
	 * @return
	 */
	public boolean isDeviceSupport()
	{
		boolean flag = AcousticEchoCanceler.isAvailable();
		return flag;
	}

	//初始化并使能AEC。
	private AcousticEchoCanceler canceler;

	public boolean initAEC(int audioSession)
	{		
		canceler = AcousticEchoCanceler.create(audioSession);
		if (canceler == null)
		{
			Log.e(TAG, "回声清除创建失败 canceler == null");
			return false;
		}
		Log.v(TAG, "回声清除创建成功");
		canceler.setEnabled(true);
		return canceler.getEnabled();
	}

	//3）使能/去使能AEC。

	public boolean setAECEnabled(boolean enable)
	{
		if (null == canceler)
		{
			return false;
		}
		canceler.setEnabled(enable);
		return canceler.getEnabled();
	}

	//4）释放AEC。
	public boolean releaseAEC()
	{
		if (null == canceler)
		{
			return false;
		}
		canceler.setEnabled(false);
		canceler.release();
		return true;
	}

	// AcousticEchoCanceler的初始化需要一个sessionid，下面简单的备忘下上层的调用方式：
	private int audioSession;

	public void tesetAEC()
	{
		// 1）初始化AudioRecord的时候需要处理第一个参数。
		if (chkNewDev())
		{
			mAudioRecord = new AudioRecord(
					MediaRecorder.AudioSource.VOICE_COMMUNICATION,
					sampleRateInHz, channelConfig, audioFormat, recordBuffSize);
		} else
		{
			mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
					sampleRateInHz, channelConfig, audioFormat, recordBuffSize);
		}
		// 2）初始化好audioRecord之后，就可以通过
		audioSession = mAudioRecord.getAudioSessionId();
		// 获取到相应的sessionid。

		// 3）初始化AudioTrack时，也需要额外的处理sessionid。

		if (chkNewDev() && mAudioRecord != null)
		{
			mAudioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
					sampleRateInHz, channelConfig, audioFormat, recordBuffSize,
					AudioTrack.MODE_STREAM, audioSession);
		} else
		{
			mAudioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
					sampleRateInHz, channelConfig, audioFormat, recordBuffSize,
					AudioTrack.MODE_STREAM);
		}
	}

	// 另外，由于API的限制，需要考虑机型不匹配的情况：

	public boolean chkNewDev()
	{
		return android.os.Build.VERSION.SDK_INT >= 16;
	}
	// 权限：

	public void initAudio()
	{
	
	      
		 mAudioManager = (AudioManager)
		 getSystemService(Context.AUDIO_SERVICE);

		// 获得缓冲区字节大小
		recordBuffSize = AudioRecord.getMinBufferSize(sampleRateInHz,
				channelConfig, audioFormat);
		playBuffSize = AudioTrack.getMinBufferSize(sampleRateInHz,
				channelConfig, audioFormat);
 
		
		// 实例化音频采集和播放音频的对象
		mAudioRecord = new AudioRecord(/*MediaRecorder.AudioSource.VOICE_COMMUNICATION*/audioSource, 44100,
				AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT,
				recordBuffSize);
		//258 260
		//audioSession = mAudioRecord.getAudioSessionId();
		mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100,
				AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT,
				playBuffSize, AudioTrack.MODE_STREAM/*,audioSession*/);		
		
		/*AudioEffect effect = new AudioEffect(AudioEffect.EFFECT_TYPE_ENV_REVERB, 
	              AudioEffect.EFFECT_TYPE_NULL, 
	              0, 
	              0); 
	 
		mAudioTrack.attachAuxEffect(effect.getId()); 
		mAudioTrack.setAuxEffectSendLevel(1.0f); */
	      
	}

	/**
	 * 蓝牙状态设置
	 */
	public void bluetoothStatus()
	{
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mAdapter == null)
		{
			Log.e(TAG, "Don't have bluetooth!");
			isConnectBluetooth = false;
		} else
		// 未打开蓝牙，提示打开。
		if (!mAdapter.isEnabled())
		{
			Log.e(TAG, "[蓝牙关闭]Please open bluetooth");
		} else
		{
			mAdapter.getProfileProxy(this, mProfileListener,
					BluetoothProfile.A2DP);
			// 找不到配对的蓝牙
			if (Device == null)
			{
				isConnectBluetooth = false;
				Log.v(TAG, "[BluetoothService]onCreate()  [mDevice is NULL]");
			}
		}
	}

	@SuppressLint("NewApi")
	private BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener()
	{
		// 已连接蓝牙设备调用
		@TargetApi(Build.VERSION_CODES.HONEYCOMB)
		@SuppressLint("NewApi")
		public void onServiceConnected(int profile, BluetoothProfile proxy)
		{
			 Log.v(TAG,"[mProfileListener] onServiceConnected() 已连接蓝牙");

			/************************************* HEADSET *************************************/

			/**************************** A2DP ****************************/
			if (profile == BluetoothProfile.A2DP)
			{
				// LogUtil.v("[profile is A2DP]");

				a2dp = (BluetoothA2dp) proxy;
				List<BluetoothDevice> connectedDevices = proxy
						.getConnectedDevices();
				
				Log.i(TAG,"[mProfileListener] profile=A2DP 连接的蓝牙设备个数： "+ connectedDevices.size());
				
				// 已连接设备时
				for (BluetoothDevice device : connectedDevices)
				{
					Log.i(TAG,
							"[BluetoothService] [BluetoothProfile.ServiceListener]A2DP  连接了设备： "
									+ device.getName());
					// ======================建立连接======================
					Device = device;
					//break;
				}
				int connectionState = a2dp.getConnectionState(Device);
				// 已配对，判断是否配对了Party rocker ，建立socket通信。否则提示连接失败
				if (connectionState == BluetoothHeadset.STATE_CONNECTED)
				{
					Log.v(TAG,
							"[mProfileListener]  onServiceConnected() [BluetoothProfile.A2DP]已连接 ");
					// 设备名包含"Party Live"
					if (Device.getName().contains(DEVICE_NAME_CONTAIN))
					{
						isConnectBluetooth = true;
						Log.i(TAG, "ACTION_CONNECT_SUCCESS");
					} else
					{
						isConnectBluetooth = false;
						Log.i(TAG, "ACTION_CONNECT_FAIL");
					}
				}
				// 没有配对，提示配对蓝牙
				else if (connectionState == BluetoothA2dp.STATE_DISCONNECTED)
				{
					Log.i(TAG, "no pair bluetooth");
					isConnectBluetooth = false;
				}
			}
		}

		@Override
		public void onServiceDisconnected(int profile)
		{
			 Log.v(TAG,"[mProfileListener] onServiceDisconnected() 蓝牙断开");
		}		
	};

	/**
	 * 建立Socket连接
	 * 
	 * @param dev
	 */
	public void createConect(BluetoothDevice dev)
	{
		Log.i(TAG,"[createConect] Link for: " + dev.getName());
		mSocketThread = new SocketThread(dev);
		mSocketThread.start();
	}
	SocketThread mSocketThread = null;
	/** SPP UUID 通用唯一识别码 */
	private final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private  BluetoothSocket mSocket = null;
	// 客户端的连接：
	// 为了初始化一个与远端设备的连接，需要先获取代表该设备的一个bluetoothdevice对象。
	// 通过bluetoothdevice对象来获取bluetoothsocket并初始化连接：
	//
	// 具体步骤：
	//
	// 使用bluetoothdevice对象里的方法createRfcommSocketToServiceRecord(UUID)来获取bluetoothsocket。
	// UUID就是匹配码。
	// 然后，调用connect（）方法来。
	// 如果远端设备接收了该连接，他们将在通信过程中共享RFFCOMM信道，并且connect（）方法返回。
	/*********************** 建立蓝牙连接 **************************/
	@SuppressLint("NewApi")
	private final class SocketThread extends Thread
	{

		// private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;

		public SocketThread(BluetoothDevice device) {
			// Use a temporary object that is later assigned to mmSocket,
			// because mmSocket is final
			BluetoothSocket tmp = null;

			mmDevice = device;

			// Get a BluetoothSocket to connect with the given BluetoothDevice
			try
				{
					// MY_UUID is the app's UUID string, also used by the server
					// code
					tmp = device.createInsecureRfcommSocketToServiceRecord(uuid);
					Log.v(TAG,"[SocketThread  Create Socket ] Success");
				} catch (IOException e)
				{

					Log.v(TAG,"[SocketThread Create Socket Error]" + e.getMessage());
				}
			// mmSocket = tmp;
			mSocket = tmp;
		}

		@TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
		@SuppressLint("NewApi")
		public void run()
		{
			// Cancel discovery because it will slow down the connection
			mAdapter.cancelDiscovery();
			

			// 等待XXms。（断开蓝牙，再次连接时，要时间准备2014-10-07）
			try
				{
					Thread.sleep(1500);
				} catch (InterruptedException e1)
				{
					e1.printStackTrace();
				}

			try
				{
					// Connect the device through the socket. This will block
					// until it succeeds or throws an exception
				Log.v(TAG,"建立Party Rocker Live设备连接中...");
					mSocket.connect();// 建立连接
					Log.v(TAG,"[SocketThreadsocket ，connect() Success]建立蓝牙设备连接成功！");
					// 连接成功，将结果返回到UI 2014-09-18
					//mHandler.sendEmptyMessage(CONNECT_SUCCESS);
				} catch (IOException connectException)
				{
					// Unable to connect; close the socket and get out
					try
						{
						Log.e(TAG,"[socket 连接失败]建立蓝牙设备连接,失败！");
						Log.e(TAG,"[mSocket.connect()] Connect Error:" + connectException.getMessage());
							// 连接失败，结果返回UI线程
						//	mHandler.sendEmptyMessage(CONNECT_FAIL);
							mSocket.close();
							// mmSocket.close();
						} catch (IOException closeException)
						{
						}
					return;
				}

			// 确保了同一时刻对于每一个类实例，其所有声明为 synchronized
			// 的成员函数中至多只有一个处于可执行状态（因为至多只有一个能够获得该类实例对应的锁），从而有效避免了类成员变量的访问冲突（只要所有可能访问类成员变量的方法均被声明为
			// synchronized）。

			synchronized (MainActivity.this)
				{
					//connected(mSocket);
					Log.v(TAG,"[ConnectThread]" + mmDevice + " is connected.");
				}
		}

		/** Will cancel an in-progress connection, and close the socket */
		public void cancel()
		{
			try
				{
					mSocket.close();
					// mmSocket.close();
				} catch (IOException e)
				{
					Log.v(TAG,"[ConnectThread cancel()] " + e.getMessage());
				}
		}
	}

	
	private RecordPlayThread mRecordPlayThread;

	public synchronized void stop()
	{
		if (!isRecording)
		{
			Log.d(TAG, "Interrupting threads...");
			mRecordPlayThread.interrupt();
			mAudioRecord.stop();
			mAudioRecord.release();
			mAudioRecord = null;
		}
	}
	class ClickEvent implements View.OnClickListener
	{
		@Override
		public void onClick(View v)
		{
			if (v == btnRecord)
			{
				/*if (!isConnectBluetooth)
				{
					tos.setText("未连接蓝牙");
					tos.show();
					return;
				}*/
				isRecording = !isRecording;
				if (isRecording)
				{
					btnRecord.setText("停止");
					mRecordPlayThread = new RecordPlayThread();
					// 开一条线程边录边放
					mRecordPlayThread.start();
				} else
				{
					btnRecord.setText("录音");
				}
			} else if (v == btnExit)
			{
				isRecording = false;
				MainActivity.this.finish();
			}
		}
	}

	class RecordPlayThread extends Thread
	{
		@Override
		public void run()
		{
			try
			{
				if (AECM_DEBUG) {
					aecm = new MobileAEC(null);
					aecm.setAecmMode(MobileAEC.AggressiveMode.MILD/*MOST_AGGRESSIVE*/)
							.prepare();
				}
				
				byte[] buffer = new byte[recordBuffSize];
				// 开始录制
				mAudioRecord.startRecording();
				// 开始播放
				mAudioTrack.play();			
				while (isRecording /*&& isConnectBluetooth*/)
				{
					// 从MIC保存数据到缓冲区buffer
					int bufferReadResult = mAudioRecord.read(buffer, 0,
							recordBuffSize);

					if (bufferReadResult == AudioRecord.ERROR_INVALID_OPERATION
							|| bufferReadResult == AudioRecord.ERROR_BAD_VALUE)
					{
						Log.e(TAG,"An error occured with the AudioRecord API !");
					} else
					{
						byte[] tmpBuf = new byte[bufferReadResult];

						System.arraycopy(buffer, 0, tmpBuf, 0, bufferReadResult);
						// tmpBuf = buffer.clone();

						/**
						 * ==================================
						 * 
						 * 
						 * 
						 * tmpBuf 采集到音频数据流
						 *
						 *
						 * ==================================
						 */
						
						/*---------增加AEC处理-----------*/
						/* final int cacheSize = tmpBuf.length;
						//byte[] pcmInputCache =tmpBuf;// new byte[tmpBuf.length];

						// core procession
						//将文件数据流读到缓冲区pcmInputCache					
						// convert bytes[] to shorts[], and make it into little endian.
						short[] aecTmpIn = new short[cacheSize / 2];
						short[] aecTmpOut = new short[cacheSize / 2];
							//通过包装的方法创建的缓冲区保留了被包装数组内保存的数据
							ByteBuffer.wrap(tmpBuf)
							//排序
							         // .order(ByteOrder.LITTLE_ENDIAN)
							//转成短整形缓冲区,放到    aecTmpIn     
									  .asShortBuffer().get(aecTmpIn);
							//回音处理
							// aecm procession, for now the echo tail is hard-coded 10ms,
							// but you
							// should estimate it correctly each time you call
							// echoCancellation, otherwise aecm
							// cannot work.
							//TODO：报错...
							aecm.farendBuffer(aecTmpIn, cacheSize / 2);
							
							aecm.echoCancellation(aecTmpIn,
									null, aecTmpOut,
									(short) (cacheSize / 2), 
									//10ms
									(short) 10);

							// output 回声消除之后的buffer
							byte[] aecBuf = new byte[cacheSize];
							ByteBuffer.wrap(aecBuf).order(ByteOrder.LITTLE_ENDIAN)
									.asShortBuffer().put(aecTmpOut);
							
						
						Log.i(TAG, "完成AEC处理");			
						mAudioTrack.write(aecBuf, 0, aecBuf.length);*/	
						/*----------------------------*/
						
						// 写入数据即播放
						mAudioTrack.write(tmpBuf, 0, tmpBuf.length);
					}
				}
				// 提示
				if (!isConnectBluetooth)
				{
					Log.e(TAG, "未连接蓝牙设备");
				}
				
				// 停止录音和播放
				mAudioRecord.stop();
				//mAudioRecord.release();
				//mAudioRecord = null;
				
				mAudioTrack.stop();				

				aecm.close();
			} catch (Throwable t)
			{
				Log.e(TAG, "播放音频失败："+t.getMessage());
				//tos.makeText(MainActivity.this, t.getMessage(), 1000).show();
			}
		}
	};

	/**
	 * 注册广播
	 */
	private void registerReceiver()
	{
		mReceiver = new Receiver();
		intentFilter = new IntentFilter();
		intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
		// 蓝牙连接
		intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		// 蓝牙断开
		intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		registerReceiver(mReceiver, intentFilter);
	}
	
	/**
	 * 注销广播
	 */
	private void unregisterReceiver()
	{
		unregisterReceiver(mReceiver);
	}

	private class Receiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			// 发生蓝牙连接
			if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action))
			{
				Log.d(TAG, "成功连接蓝牙");
				isConnectBluetooth = true;
			} else
			// 断开连接
			if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action))
			{
				isConnectBluetooth = false;
				Log.d(TAG, "没连接蓝牙");
			} else 
			//通道切换的事件.耳机被拔出
			if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action))
			{
				// 声音通道，切换
				// Pause the playback				
				// mAudioManager.setBluetoothA2dpOn(true);
				Log.d(TAG, "ACTION_AUDIO_BECOMING_NOISY 输出声道类型发生改变 "
				//true:蓝牙设备播放声音
				 +"  isBluetoothA2dpOn="+mAudioManager.isBluetoothA2dpOn()
				// +"  isSpeakerphoneOn="+mAudioManager.isSpeakerphoneOn()
						);
			}
		}
	}
	
	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		isRecording = false;
		releaseAEC();
		unregisterReceiver();
		android.os.Process.killProcess(android.os.Process.myPid());
	}
}

	//麦克风就是话筒。如何降噪，去触摸杂音，回声处理
	/**
	 * Acoustic Echo Cancelling
	 * 
	 * 
	 *
	 */