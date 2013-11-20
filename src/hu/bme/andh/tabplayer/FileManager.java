package hu.bme.andh.tabplayer;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;

import android.content.Context;
import android.os.Environment;
import android.widget.Toast;

public class FileManager {

	private ArrayList<HashMap<String, String>> songsList = new ArrayList<HashMap<String, String>>();
	Context context1;

	public FileManager(Context context) {
		context1 = context;
	}

	/**
	 * Function to read all mp3 files from sdcard/Music and store the details in
	 * ArrayList
	 * */
	public ArrayList<HashMap<String, String>> getPlayList() {
		
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) || Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {	
			File home = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
			
			if (home.listFiles(new FileExtensionFilter()).length > 0) {
				for (File file : home.listFiles(new FileExtensionFilter())) {
					HashMap<String, String> song = new HashMap<String, String>();
					song.put( "songTitle" , file.getName().substring( 0 , (file.getName().length() - 4) ) );
					song.put( "songPath" , file.getPath() );

					// Adding each song to SongList
					songsList.add(song);
				}
				// return songs list array
				return songsList;
			} else {
				Toast.makeText(context1, "Couldn't find any audio file",
						Toast.LENGTH_LONG).show();
				return songsList;
			}
		} else {
			Toast.makeText(context1,
					"SD Card is either mounted elsewhere or is unusable",
					Toast.LENGTH_LONG).show();
		}
		return songsList;
	}

	/**
	 * Class to filter files which are having .mp3, .mid, .wav, .ogg extension
	 * */
	class FileExtensionFilter implements FilenameFilter {
		public boolean accept(File dir, String name) {
			return (name.endsWith(".mp3") || name.endsWith(".MP3")
					|| name.endsWith(".mid") || name.endsWith(".MID")
					|| name.endsWith(".wav") || name.endsWith(".WAV")
					|| name.endsWith(".ogg") || name.endsWith(".OGG"));
		}
	}
}
