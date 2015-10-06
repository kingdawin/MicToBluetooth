package com.homni.audiobluetooth;

import java.util.List;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
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
 *<p>
 *假设:
 *通过套接字（Socket）实现了音频数据的网络传输，
 *做到了一端使用AudioRecord获取音频流然后通过套接字传输出去，
 *而另一端通过套接字接收后使用AudioTrack类播放
 *<p>
 *
 *将存放录音数据的数组放到LinkedList中,
 *当LinkedList中数组个数达到2（这个也是经过试验验证话音质量最好时的数据）时，
 *将先录好的数组中数据传送出去。经过上述反复试验和修改，
 *最终使双方通话质量较好，且延时较短（大概有2秒钟）。
 *<p>
 */
public class MainActivity extends Activity
{
	private static final String TAG = "MainActivity";

	// private AudioManager mAudioManager;

	private Button btnRecord,/* btnStop,*/ btnExit;
	// 缓冲区字节大小
	private int recordBuffSize, playBuffSize;
	private boolean isRecording;
	/**是否连接蓝牙*/
	private boolean isConnectBluetooth;
	
	// 采集音频的api是android.media.AudioRecord类
	// 在Android中录音可以用MediaRecord录音，操作比较简单。
	// 但是不够专业，就是不能对音频进行处理。如果要进行音频的实时的处理或者音频的一些封装
	// 就可以用AudioRecord来进行录音
	private AudioRecord mAudioRecord;
	/**播放音频，用write方法*/
	private AudioTrack mAudioTrack;

	// 音频获取源 :麦克风
	private int audioSource = MediaRecorder.AudioSource.MIC;
	// 44.1K
	private int sampleRateInHz = 44100;
	// 设置音频的录制的声道CHANNEL_IN_STEREO为双声道，CHANNEL_CONFIGURATION_MONO为单声道
	private int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
	// 音频数据格式:PCM 16位每个样本。保证设备支持。PCM 8位每个样本。不一定能得到设备支持。
	private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;


	private Receiver mReceiver;
	private IntentFilter intentFilter;

	// 代表了一个本地的蓝牙适配器。他是所有蓝牙交互的的入口点。
	// 利用它你可以发现其他蓝牙设备，查询绑定了的设备，使用已知的MAC地址实例化一个蓝牙设备和建立一个BluetoothServerSocket（作为服务器端）来监听来自其他设备的连接。
	private BluetoothAdapter mAdapter = null;
	private Toast tos;

	private BluetoothHeadset headset;
	private BluetoothDevice Device;
	private BluetoothA2dp a2dp;
	/** 蓝牙设备名包含的单词 */
	private static final String DEVICE_NAME_CONTAIN = "Live";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		tos = Toast.makeText(this, "", 0);
		// mAudioManager = (AudioManager)
		// getSystemService(Context.AUDIO_SERVICE);

		// 获得缓冲区字节大小
		recordBuffSize = AudioRecord.getMinBufferSize(
				sampleRateInHz,
				channelConfig,
				audioFormat);
		playBuffSize = AudioTrack.getMinBufferSize(
				sampleRateInHz,
				channelConfig,
				audioFormat);

		// 实例化音频采集和播放音频的对象
		mAudioRecord = new AudioRecord(audioSource,
				 44100,
				 AudioFormat.CHANNEL_IN_STEREO,
				 AudioFormat.ENCODING_PCM_16BIT,
				 recordBuffSize);
		mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
				 44100,
				 AudioFormat.CHANNEL_IN_STEREO, 
				 AudioFormat.ENCODING_PCM_16BIT,
				 playBuffSize,
				 AudioTrack.MODE_STREAM);

		btnRecord = (Button) this.findViewById(R.id.btnRecord);
		//btnStop = (Button) this.findViewById(R.id.btnStop);
		btnExit = (Button) this.findViewById(R.id.btnExit);

		btnRecord.setOnClickListener(new ClickEvent());
		//btnStop.setOnClickListener(new ClickEvent());
		btnExit.setOnClickListener(new ClickEvent());

		bluetoothStatus();
		System.out.println("AudioRecord.getMinBufferSize=" + recordBuffSize);
		registerReceiver();
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
			// registerReceiver();
			mAdapter.getProfileProxy(this,
					mProfileListener,
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
			// LogUtil.v("[BluetoothService] [BluetoothProfile.ServiceListener] onServiceConnected()");

			/************************************* HEADSET *************************************/

			/**************************** A2DP ****************************/
			if (profile == BluetoothProfile.A2DP)
			{
				// LogUtil.v("[profile is A2DP]");

				a2dp = (BluetoothA2dp) proxy;
				List<BluetoothDevice> connectedDevices = proxy
						.getConnectedDevices();
				// 已连接设备时
				for (BluetoothDevice device : connectedDevices)
				{
					Device = device;
					Log.i(TAG,
							"[BluetoothService] [BluetoothProfile.ServiceListener]A2DP  连接了设备： "
									+ Device.getName());
					// ======================建立连接======================
				}
				Log.i(TAG, "connectedDevices.size=" + connectedDevices.size());
				int connectionState = a2dp.getConnectionState(Device);
				// 已配对，判断是否配对了Party rocker ，建立socket通信。否则提示连接失败
				if (connectionState == BluetoothHeadset.STATE_CONNECTED)
				{
					Log.v(TAG,
							"[BluetoothService] [BluetoothProfile.ServiceListener] onServiceConnected()[BluetoothProfile.A2DP]已连接 ");
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
			// TODO Auto-generated method stub

		}
	};

	private RecordPlayThread mRecordPlayThread;

	class ClickEvent implements View.OnClickListener
	{
		@Override
		public void onClick(View v)
		{
			if (v == btnRecord)
			{
				if(!isConnectBluetooth)
				{
					tos.setText("未连接蓝牙");
					tos.show();
					return;
				}
				isRecording = !isRecording;
				if(isRecording)
				{
					btnRecord.setText("停止");
					mRecordPlayThread = new RecordPlayThread();
					// 开一条线程边录边放
					mRecordPlayThread.start();
				}else {
					btnRecord.setText("录音");
				}
			} /*else if (v == btnStop)
			{
				isRecording = false;
			}*/ else if (v == btnExit)
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
				byte[] buffer = new byte[recordBuffSize];
				// 开始录制
				mAudioRecord.startRecording();
				// 开始播放
				mAudioTrack.play();

				while (isRecording &&isConnectBluetooth )
				{
					// 从MIC保存数据到缓冲区buffer
					int bufferReadResult = mAudioRecord.read(buffer, 0,
							recordBuffSize);
					byte[] tmpBuf = new byte[bufferReadResult];

					System.arraycopy(buffer, 0, tmpBuf, 0, bufferReadResult);
					// tmpBuf = buffer.clone();
					
					/**
					 * ==================================  
					 * tmpBuf 采集到音频数据流
					 *
					 *
					 *
					 * ==================================
					 */
					// 写入数据即播放
					mAudioTrack.write(tmpBuf, 0, tmpBuf.length);
				}
				//提示
				if(!isConnectBluetooth)
				{
					Log.e(TAG,"未连接蓝牙设备");
					//tos.setText("未连接蓝牙设备");
					//tos.show();
				}			
				// 停止录音和播放
				mAudioTrack.stop();
				mAudioRecord.stop();
			} catch (Throwable t)
			{
				tos.makeText(MainActivity.this, t.getMessage(), 1000).show();
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
			} else if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action))
			{
				// 声音通道，切换
				// Pause the playback
				// mAudioManager.setBluetoothA2dpOn(true);
				Log.d(TAG, "ACTION_AUDIO_BECOMING_NOISY 输出声道类型发生改变");
			}
		}
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();		
		isRecording = false;
		unregisterReceiver();
		android.os.Process.killProcess(android.os.Process.myPid());  
	}

}
