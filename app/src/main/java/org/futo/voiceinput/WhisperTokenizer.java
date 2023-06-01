package org.futo.voiceinput;

import android.content.Context;
import android.content.res.Resources;

import org.futo.voiceinput.R;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class WhisperTokenizer {
	public static String[] TOKENS;

	static boolean initialized = false;
	public static void init(Context context){
		if(initialized) return;

		initialized = true;
		Resources resources = context.getResources();

		try {
			InputStream input = resources.openRawResource(R.raw.whisper_tokens);

			BufferedReader r = new BufferedReader(new InputStreamReader(input));

			TOKENS = new String[65536];
			int i = 0;
			for (String line; (line = r.readLine()) != null; ) {
				TOKENS[i++] = line;
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

	public static String convertTokensToString(TensorBuffer buffer) {
		assert(buffer.getDataType() == DataType.FLOAT32);
		float[] ints = buffer.getFloatArray();

		String s = "";
		for(float f : ints) {
			int i = (int)f;
			if(TOKENS[i].equals("<|endoftext|>")) break;
			if(TOKENS[i].startsWith("<|")) continue;

			s += TOKENS[i];
		}

		return s.trim();
	}
}
