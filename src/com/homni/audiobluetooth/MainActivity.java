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
 * ¼�����ţ�ֻҪ�������������������豸��������
 * 
 * @author admin
 *
 *<p>
 *����:
 *ͨ���׽��֣�Socket��ʵ������Ƶ���ݵ����紫�䣬
 *������һ��ʹ��AudioRecord��ȡ��Ƶ��Ȼ��ͨ���׽��ִ����ȥ��
 *����һ��ͨ���׽��ֽ��պ�ʹ��AudioTrack�ಥ��
 *<p>
 *
 *�����¼�����ݵ�����ŵ�LinkedList��,
 *��LinkedList����������ﵽ2�����Ҳ�Ǿ���������֤�����������ʱ�����ݣ�ʱ��
 *����¼�õ����������ݴ��ͳ�ȥ��������������������޸ģ�
 *����ʹ˫��ͨ�������Ϻã�����ʱ�϶̣������2���ӣ���
 *<p>
 */
public class MainActivity extends Activity
{
	private static final String TAG = "MainActivity";

	// private AudioManager mAudioManager;

	private Button btnRecord,/* btnStop,*/ btnExit;
	// �������ֽڴ�С
	private int recordBuffSize, playBuffSize;
	private boolean isRecording;
	/**�Ƿ���������*/
	private boolean isConnectBluetooth;
	
	// �ɼ���Ƶ��api��android.media.AudioRecord��
	// ��Android��¼��������MediaRecord¼���������Ƚϼ򵥡�
	// ���ǲ���רҵ�����ǲ��ܶ���Ƶ���д������Ҫ������Ƶ��ʵʱ�Ĵ��������Ƶ��һЩ��װ
	// �Ϳ�����AudioRecord������¼��
	private AudioRecord mAudioRecord;
	/**������Ƶ����write����*/
	private AudioTrack mAudioTrack;

	// ��Ƶ��ȡԴ :��˷�
	private int audioSource = MediaRecorder.AudioSource.MIC;
	// 44.1K
	private int sampleRateInHz = 44100;
	// ������Ƶ��¼�Ƶ�����CHANNEL_IN_STEREOΪ˫������CHANNEL_CONFIGURATION_MONOΪ������
	private int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
	// ��Ƶ���ݸ�ʽ:PCM 16λÿ����������֤�豸֧�֡�PCM 8λÿ����������һ���ܵõ��豸֧�֡�
	private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;


	private Receiver mReceiver;
	private IntentFilter intentFilter;

	// ������һ�����ص������������������������������ĵ���ڵ㡣
	// ����������Է������������豸����ѯ���˵��豸��ʹ����֪��MAC��ַʵ����һ�������豸�ͽ���һ��BluetoothServerSocket����Ϊ�������ˣ����������������豸�����ӡ�
	private BluetoothAdapter mAdapter = null;
	private Toast tos;

	private BluetoothHeadset headset;
	private BluetoothDevice Device;
	private BluetoothA2dp a2dp;
	/** �����豸�������ĵ��� */
	private static final String DEVICE_NAME_CONTAIN = "Live";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		tos = Toast.makeText(this, "", 0);
		// mAudioManager = (AudioManager)
		// getSystemService(Context.AUDIO_SERVICE);

		// ��û������ֽڴ�С
		recordBuffSize = AudioRecord.getMinBufferSize(
				sampleRateInHz,
				channelConfig,
				audioFormat);
		playBuffSize = AudioTrack.getMinBufferSize(
				sampleRateInHz,
				channelConfig,
				audioFormat);

		// ʵ������Ƶ�ɼ��Ͳ�����Ƶ�Ķ���
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
	 * ����״̬����
	 */
	public void bluetoothStatus()
	{
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mAdapter == null)
		{
			Log.e(TAG, "Don't have bluetooth!");
			isConnectBluetooth = false;
		} else
		// δ����������ʾ�򿪡�
		if (!mAdapter.isEnabled())
		{
			Log.e(TAG, "[�����ر�]Please open bluetooth");
		} else
		{
			// registerReceiver();
			mAdapter.getProfileProxy(this,
					mProfileListener,
					BluetoothProfile.A2DP);
			// �Ҳ�����Ե�����
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
		// �����������豸����
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
				// �������豸ʱ
				for (BluetoothDevice device : connectedDevices)
				{
					Device = device;
					Log.i(TAG,
							"[BluetoothService] [BluetoothProfile.ServiceListener]A2DP  �������豸�� "
									+ Device.getName());
					// ======================��������======================
				}
				Log.i(TAG, "connectedDevices.size=" + connectedDevices.size());
				int connectionState = a2dp.getConnectionState(Device);
				// ����ԣ��ж��Ƿ������Party rocker ������socketͨ�š�������ʾ����ʧ��
				if (connectionState == BluetoothHeadset.STATE_CONNECTED)
				{
					Log.v(TAG,
							"[BluetoothService] [BluetoothProfile.ServiceListener] onServiceConnected()[BluetoothProfile.A2DP]������ ");
					// �豸������"Party Live"
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
				// û����ԣ���ʾ�������
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
					tos.setText("δ��������");
					tos.show();
					return;
				}
				isRecording = !isRecording;
				if(isRecording)
				{
					btnRecord.setText("ֹͣ");
					mRecordPlayThread = new RecordPlayThread();
					// ��һ���̱߳�¼�߷�
					mRecordPlayThread.start();
				}else {
					btnRecord.setText("¼��");
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
				// ��ʼ¼��
				mAudioRecord.startRecording();
				// ��ʼ����
				mAudioTrack.play();

				while (isRecording &&isConnectBluetooth )
				{
					// ��MIC�������ݵ�������buffer
					int bufferReadResult = mAudioRecord.read(buffer, 0,
							recordBuffSize);
					byte[] tmpBuf = new byte[bufferReadResult];

					System.arraycopy(buffer, 0, tmpBuf, 0, bufferReadResult);
					// tmpBuf = buffer.clone();
					
					/**
					 * ==================================  
					 * tmpBuf �ɼ�����Ƶ������
					 *
					 *
					 *
					 * ==================================
					 */
					// д�����ݼ�����
					mAudioTrack.write(tmpBuf, 0, tmpBuf.length);
				}
				//��ʾ
				if(!isConnectBluetooth)
				{
					Log.e(TAG,"δ���������豸");
					//tos.setText("δ���������豸");
					//tos.show();
				}			
				// ֹͣ¼���Ͳ���
				mAudioTrack.stop();
				mAudioRecord.stop();
			} catch (Throwable t)
			{
				tos.makeText(MainActivity.this, t.getMessage(), 1000).show();
			}
		}
	};

	/**
	 * ע��㲥
	 */
	private void registerReceiver()
	{
		mReceiver = new Receiver();
		intentFilter = new IntentFilter();
		intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
		// ��������
		intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		// �����Ͽ�
		intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		registerReceiver(mReceiver, intentFilter);
	}

	/**
	 * ע���㲥
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

			// ������������
			if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action))
			{
				Log.d(TAG, "�ɹ���������");
				isConnectBluetooth = true;
			} else
			// �Ͽ�����
			if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action))
			{
				isConnectBluetooth = false;
				Log.d(TAG, "û��������");
			} else if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action))
			{
				// ����ͨ�����л�
				// Pause the playback
				// mAudioManager.setBluetoothA2dpOn(true);
				Log.d(TAG, "ACTION_AUDIO_BECOMING_NOISY ����������ͷ����ı�");
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
