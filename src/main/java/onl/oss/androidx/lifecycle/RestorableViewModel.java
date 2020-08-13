package onl.oss.androidx.lifecycle;

import android.os.Bundle;
import android.os.Parcel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RestorableViewModel extends ViewModel {

    private static final String VALUES = "values";
    private static final String KEYS = "keys";

    private File file;
    private boolean isSaveRequired = true;
    private Map<String, Object> state;
    private Map<String, MutableLiveData<?>> cache = new HashMap<String, MutableLiveData<?>>();

    /* package private */ void initialize(File file, boolean restore, Bundle defaultState) {
        if(this.file != null || this.state != null) {
            throw new IllegalStateException("Already initialized");
        }
        this.file = file;
        this.state = new HashMap<String, Object>();
        if(defaultState != null) {
            for (String key : defaultState.keySet()) {
                state.put(key, defaultState.get(key));
            }
        }
        if(restore) {
            loadState();
        }
    }

    /* package private */ void setSaveRequired(boolean b) {
        isSaveRequired = b;
    }

    private void loadState() {
        try {
            byte[] data;
            synchronized (getClass()) {
                data = readAllBytes(file);
            }
            Parcel parcel = Parcel.obtain();
            parcel.unmarshall(data, 0, data.length);
            parcel.setDataPosition(0);
            Bundle bundle = parcel.readBundle();

            List<?> keys = bundle.getParcelableArrayList(KEYS);
            List<?> values = bundle.getParcelableArrayList(VALUES);
            if(keys == null || values == null || keys.size() != values.size()) {
                throw new IOException("Invalid bundle passed as restored state");
            }
            for(int i = 0; i < keys.size(); i++) {
                state.put((String)keys.get(i), values.get(i));
            }
        } catch(IOException ignore) {
            ignore.printStackTrace();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    /* package private */ void saveState() {
        ArrayList keys = new ArrayList(state.size());
        ArrayList values = new ArrayList(state.size());
        for(Map.Entry<String, Object> entry : state.entrySet()) {
            keys.add(entry.getKey());
            values.add(entry.getValue());
        }
        Parcel parcel = Parcel.obtain();
        Bundle bundle = new Bundle();
        bundle.putParcelableArrayList(KEYS, keys);
        bundle.putParcelableArrayList(VALUES, values);
        bundle.writeToParcel(parcel, 0);
        try {
            synchronized (getClass()) {
                writeAllBytes(file, parcel.marshall());
            }
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> MutableLiveData<T> getLiveData(String key) {
        @SuppressWarnings("unchecked")
        MutableLiveData<T> liveData = (MutableLiveData<T>)cache.get(key);
        if(liveData != null) {
            return liveData;
        }
        if(state.containsKey(key)) {
            @SuppressWarnings("unchecked")
            T value = (T)state.get(key);
            liveData = new RestorableLiveData<T>(key, value);
        } else {
            liveData = new RestorableLiveData<T>(key);
        }
        cache.put(key, liveData);
        return liveData;
    }

    protected <T> MutableLiveData<T> getLiveData(String key, T initialValue) {
        @SuppressWarnings("unchecked")
        MutableLiveData<T> liveData = (MutableLiveData<T>)cache.get(key);
        if(liveData != null) {
            return liveData;
        }
        if(state.containsKey(key)) {
            @SuppressWarnings("unchecked")
            T value = (T)state.get(key);
            liveData = new RestorableLiveData<T>(key, value);
        } else {
            liveData = new RestorableLiveData<T>(key, initialValue);
        }
        cache.put(key, liveData);
        return liveData;
    }

    private class RestorableLiveData<T> extends MutableLiveData<T> {
        private String key;

        RestorableLiveData(String key, T value) {
            super(value);
            this.key = key;
        }

        RestorableLiveData(String key) {
            super();
            this.key = key;
        }

        @Override
        public void setValue(T value) {
            if(state != null) {
                state.put(key, value);
                if(isSaveRequired) {
                    saveState();
                }
            }
            super.setValue(value);
        }
    }

    private static byte[] readAllBytes(File file) throws IOException {
        ByteArrayOutputStream out = null;
        BufferedInputStream in = null;
        try {
            in = new BufferedInputStream(new FileInputStream(file));
            out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int size;
            while ((size = in.read(buf, 0, 8192)) != -1) {
                out.write(buf, 0, size);
            }
        } finally {
            if(out != null) {
                try { out.close(); } catch(IOException e) {}
            }
            if(in != null) {
                try { in.close(); } catch(IOException e) {}
            }
        }
        return out.toByteArray();
    }

    private static void writeAllBytes(File file, byte[] data) throws IOException {
        BufferedOutputStream out = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(file, false));
            out.write(data);
        } finally {
            if(out != null) {
                try { out.close(); } catch(IOException e) {}
            }
        }
    }
}
