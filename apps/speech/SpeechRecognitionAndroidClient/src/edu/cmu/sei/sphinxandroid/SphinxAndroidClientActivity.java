/**
---------------------
Copyright 2012 Carnegie Mellon University

This material is based upon work funded and supported by the Department of Defense under Contract No. 
FA8721-05-C-0003 with Carnegie Mellon University for the operation of the Software Engineering Institute, 
a federally funded research and development center.

Any opinions, findings and conclusions or recommendations expressed in this material are those of the 
author(s) and do not necessarily reflect the views of the United States Department of Defense.

NO WARRANTY
THIS CARNEGIE MELLON UNIVERSITY AND SOFTWARE ENGINEERING INSTITUTE MATERIAL IS FURNISHED ON AN �AS-IS� 
BASIS. CARNEGIE MELLON UNIVERSITY MAKES NO WARRANTIES OF ANY KIND, EITHER EXPRESSED OR IMPLIED, AS TO ANY 
MATTER INCLUDING, BUT NOT LIMITED TO, WARRANTY OF FITNESS FOR PURPOSE OR MERCHANTABILITY, EXCLUSIVITY, 
OR RESULTS OBTAINED FROM USE OF THE MATERIAL. CARNEGIE MELLON UNIVERSITY DOES NOT MAKE ANY WARRANTY OF 
ANY KIND WITH RESPECT TO FREEDOM FROM PATENT, TRADEMARK, OR COPYRIGHT INFRINGEMENT.

This material contains SEI Proprietary Information and may not be disclosed outside of the SEI without 
the written consent of the Director�s Office and completion of the Disclosure of Information process.
------------
**/

package edu.cmu.sei.sphinxandroid;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class SphinxAndroidClientActivity extends Activity implements OnClickListener
{
	public static final String LOG_KEY = "LOG_KEY";
	
	public static final int MENU_ID_SETTINGS = 92189;
	public static final int MENU_ID_CLEAR = 111163;

	private String ipAddress;
	private int portNumber;
	private String directoryString;

	private Socket socket;
	private File directory;
	private List<File> fileList;

	private DataOutputStream outToServer;
	private DataInputStream inFromServer;

	private TextView textView;
	private TextView currentDirTextView;
	private Button sendButton;
	
	private String log = "";
	
	private long requestSendTime = 0L;
	private long responseReceivedTime = 0L;
	private long rttForCurrentRequest = 0L;
	private long rttForPreviousRequest = 0L;

	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		textView = (TextView)findViewById(R.id.text);
		textView.setText( log );
		currentDirTextView = (TextView)findViewById(R.id.current_dir_text);
		
		sendButton = (Button)findViewById(R.id.send_button);
		sendButton.setOnClickListener( this );

		loadPreferneces();

		directory = new File( directoryString );
		if( !directory.exists() )
		{
			Toast.makeText( this, "Directory does not exist", Toast.LENGTH_SHORT).show();
		}

		fileList = Arrays.asList( directory.listFiles( new FileFilter() 
		{
			@Override
			public boolean accept(File pathname) 
			{
				if( pathname.toString().endsWith(".wav")
						|| pathname.toString().endsWith(".WAV") )
				{
					return true;
				}
				return false;
			}
		}));
	}

	@Override
	protected void onResume() 
	{
		loadPreferneces();
		
		super.onResume();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		menu.add(0, MENU_ID_SETTINGS, 0, getString( R.string.menu_settings) );
		menu.add(0, MENU_ID_CLEAR, 1, getString( R.string.menu_clear) );
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) 
	{
		switch( item.getItemId() )
		{
		case MENU_ID_SETTINGS:
			startActivity( new Intent( SphinxAndroidClientActivity.this, PreferenceActivity.class) );
			break;
		case MENU_ID_CLEAR:
			log = "";
			textView.setText( log );
			break;
		default:
			break;
		}
		return super.onMenuItemSelected(featureId, item);
	}

	public void loadPreferneces()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences( this );
		this.ipAddress = prefs.getString( getString( R.string.pref_ipaddress), getString(R.string.default_ipaddress) );
		this.portNumber = Integer.parseInt( prefs.getString(getString( R.string.pref_portnumber), getString(R.string.default_portnumber)) );
		this.directoryString = prefs.getString( getString( R.string.pref_directory), getString(R.string.default_directory));
		currentDirTextView.setText( directoryString );
	}
	
	
	/**
	 * @author gmcahill
	 * A sync task for sending audio
	 */
	class SendAudio extends AsyncTask<Void,String,String>
	{
		ProgressDialog progreeDialog;

		@Override
		protected void onPreExecute() 
		{
			progreeDialog = new ProgressDialog( SphinxAndroidClientActivity.this );
			progreeDialog.setCancelable( false );
			
			progreeDialog.setMessage("Connecting to server...");
			updateLog( "Connecting to server..." );
			progreeDialog.show();
			super.onPreExecute();
			
		}

		@Override
		protected void onPostExecute(String result) 
		{
			progreeDialog.dismiss();
			if( result == null )
			{
				updateLog( "No response or error from server...");
			}
			super.onPostExecute(result);
		}


		@Override
		protected void onProgressUpdate(String... values) 
		{
			progreeDialog.setMessage( values[0] );
			updateLog( values[0] );
			super.onProgressUpdate(values);
		}

		@Override
		protected String doInBackground(Void... params) 
		{
			String response = null;
			try 
			{
				socket = new Socket();
				socket.connect(new InetSocketAddress( ipAddress, portNumber ), 5000 );
				publishProgress("Connected to server " + socket.getInetAddress() +" on port " + socket.getPort() );

				outToServer = new DataOutputStream( socket.getOutputStream() );
				inFromServer = new DataInputStream( socket.getInputStream() );

				int filesProccessed = 1;
				int fileCount = fileList.size();
				for( final File file: fileList )
				{
					publishProgress("Sending " +filesProccessed +" / " + fileCount +" file(s) \n" 
							+file.getName() +"\n" +"File size is " + file.length() +" bytes" );
					requestSendTime = System.currentTimeMillis();
					sendSpeechRequest( file );
					publishProgress("Finished sending " +file.getName() );
					
					publishProgress("Getting response from server..." );
					int responseSize = inFromServer.readInt();
					publishProgress("Response size is " + responseSize +" bytes" );
					
					if(responseSize > 0 )
					{
						byte[] byteBuffer = new byte[responseSize];
						inFromServer.read(byteBuffer);
						responseReceivedTime = System.currentTimeMillis();
						rttForCurrentRequest = responseReceivedTime - requestSendTime;
						response = new String(byteBuffer);
						publishProgress( "----------");
						publishProgress( response );
						publishProgress("Request Send Time: " + requestSendTime );
						publishProgress("Response Recieved Time: " + responseReceivedTime );
						publishProgress("RTT Current Request: " + rttForCurrentRequest );
						publishProgress("RTT Previous Request: " + rttForPreviousRequest );
						publishProgress( "----------");
						rttForPreviousRequest = rttForCurrentRequest;
					}
					filesProccessed++;
				}

			} 
			catch (UnknownHostException e) 
			{
				publishProgress("An UnknownHostException has occured.");
				e.printStackTrace();
				return null;
			} 
			catch (IOException e) 
			{
				publishProgress("An IOException has occured.");
				e.printStackTrace();
				return null;
			}
			return response;
		}
	}
	
	public void sendSpeechRequest( File file ) 
	{
		try 
		{
			int fileLength = (int)(file.length());
			outToServer.writeLong( fileLength );
			FileInputStream fis = new FileInputStream( file );
			byte[] buffer = new byte[ fileLength ];
			fis.read( buffer );
			
			outToServer.write( buffer );
		} 
		catch (IOException io) 
		{
			io.printStackTrace();
		}
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) 
	{
		outState.putString( LOG_KEY, log );
		super.onSaveInstanceState(outState);
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) 
	{
		log = savedInstanceState.getString( LOG_KEY );
		textView.setText( log );
		super.onRestoreInstanceState(savedInstanceState);
	}

	
	@Override
	public void onClick(View v) 
	{
		if( v.equals( sendButton ) )
		{
			new SendAudio().execute();
		}
	}
	
	public void updateLog( String text )
	{
		log = log +"\n" + text;
		textView.setText( log );
	}
}