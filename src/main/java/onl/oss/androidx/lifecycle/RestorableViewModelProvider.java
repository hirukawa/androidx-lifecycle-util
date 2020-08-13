package onl.oss.androidx.lifecycle;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import androidx.activity.ComponentActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class RestorableViewModelProvider implements LifecycleObserver {

    private static final String DIR = ".restorable-viewmodel-states";

    private static Map<Class<?>, Map<Class<? extends RestorableViewModel>, RestorableViewModel>> viewModelStoreByClass;

    private static Map<Class<? extends RestorableViewModel>, RestorableViewModel> getViewModelStoreByClass(Class<?> cls) {
        if(viewModelStoreByClass == null) {
            viewModelStoreByClass = new HashMap<Class<?>, Map<Class<? extends RestorableViewModel>, RestorableViewModel>>();
        }
        Map<Class<? extends RestorableViewModel>, RestorableViewModel> viewModelStore = viewModelStoreByClass.get(cls);
        if(viewModelStore == null) {
            viewModelStore = new HashMap<Class<? extends RestorableViewModel>, RestorableViewModel>();
            viewModelStoreByClass.put(cls, viewModelStore);
        }
        return viewModelStore;
    }

    private File dir;
    private boolean restore;
    private Bundle defaultState;
    private Lifecycle lifecycle;
    private boolean isSaveRequired = true;
    private Map<Class<? extends RestorableViewModel>, RestorableViewModel> viewModelStore;

    public RestorableViewModelProvider(Activity activity, Bundle savedInstanceState) {
        this(activity, activity.getClass().getCanonicalName(), savedInstanceState);
        defaultState = activity.getIntent().getExtras();
        viewModelStore = getViewModelStoreByClass(activity.getClass());
        if(activity instanceof ComponentActivity) {
            lifecycle = ((ComponentActivity)activity).getLifecycle();
            lifecycle.addObserver(this);
        }
    }

    public RestorableViewModelProvider(Fragment fragment, Bundle savedInstanceState) {
        this(fragment.getContext(), fragment.getClass().getCanonicalName(), savedInstanceState);
        defaultState = fragment.getArguments();
        viewModelStore = getViewModelStoreByClass(fragment.getClass());
        lifecycle = fragment.getLifecycle();
        lifecycle.addObserver(this);
    }

    private RestorableViewModelProvider(Context context, String name, Bundle savedInstanceState) {
        this.dir = new File(new File(context.getNoBackupFilesDir(), DIR), ".provider-" + name);
        this.restore = savedInstanceState != null;

        if(!restore) {
            clearAllStates();
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    protected void onResume() {
        isSaveRequired = false;
        for(RestorableViewModel viewModel : viewModelStore.values()) {
            viewModel.setSaveRequired(isSaveRequired);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    protected void onPause() {
        isSaveRequired = true;
        for(RestorableViewModel viewModel : viewModelStore.values()) {
            viewModel.setSaveRequired(isSaveRequired);
            viewModel.saveState();
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    protected void onDestroy() {
        if(lifecycle != null) {
            lifecycle.removeObserver(this);
        }
    }

    private void clearAllStates() {
        if(!dir.exists() || !dir.isDirectory()) {
            return;
        }

        for(File file : dir.listFiles()) {
            if(file.delete() == false) {
                throw new RuntimeException("Can't delete file: " + file);
            }
        }
    }

    public <T extends RestorableViewModel> T get(Class<T> viewModelClass) {
        @SuppressWarnings("unchecked")
        T viewModel = (T)viewModelStore.get(viewModelClass);
        if(viewModel == null) {
            if(!dir.exists()) {
                if(dir.mkdirs() == false) {
                    throw new RuntimeException("Can't create directory: " + dir);
                }
            }
            if(!dir.isDirectory()) {
                throw new RuntimeException("Not a directory: " + dir);
            }
            File file = new File(dir, ".state-" + viewModelClass.getCanonicalName());
            try {
                viewModel = viewModelClass.newInstance();
                viewModel.initialize(file, restore, defaultState);
                viewModel.setSaveRequired(isSaveRequired);
            } catch(IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch(InstantiationException e) {
                throw new RuntimeException(e);
            }
            viewModelStore.put(viewModelClass, viewModel);
        }
        return viewModel;
    }
}
