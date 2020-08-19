package onl.oss.androidx.lifecycle;

import android.content.Context;
import android.os.Bundle;

import androidx.activity.ComponentActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ViewModelStoreOwner;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
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

    private ViewModelStoreOwner owner;
    private File dir;
    private boolean restore;
    private Bundle defaultState;
    private Lifecycle lifecycle;
    private boolean isSaveRequired = true;
    private Map<Class<? extends RestorableViewModel>, RestorableViewModel> viewModelStore;

    public RestorableViewModelProvider(ComponentActivity activity, Bundle savedInstanceState) {
        this(activity, activity.getClass().getCanonicalName(), savedInstanceState);
        owner = activity;
        defaultState = activity.getIntent().getExtras();
        viewModelStore = getViewModelStoreByClass(activity.getClass());
        if(!restore) {
            viewModelStore.clear();
        }
        lifecycle = activity.getLifecycle();
        lifecycle.addObserver(this);
    }

    public RestorableViewModelProvider(Fragment fragment, Bundle savedInstanceState) {
        this(fragment.getContext(), fragment.getClass().getCanonicalName(), savedInstanceState);
        owner = fragment;
        defaultState = fragment.getArguments();
        viewModelStore = getViewModelStoreByClass(fragment.getClass());
        if(!restore) {
            viewModelStore.clear();
        }
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

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    protected void onStart() {
        isSaveRequired = false;
        for(RestorableViewModel viewModel : viewModelStore.values()) {
            viewModel.setSaveRequired(isSaveRequired);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    protected void onStop() {
        isSaveRequired = true;
        // 停止要因が画面回転等の構成変更によるものか調べます。
        boolean isChangingConfigurations = false;
        if(owner instanceof ComponentActivity) {
            isChangingConfigurations = ((ComponentActivity)owner).isChangingConfigurations();
        } else if(owner instanceof Fragment) {
            isChangingConfigurations = ((Fragment)owner).requireActivity().isChangingConfigurations();
        }
        // 画面回転等の構成変更による停止の場合はすぐにアクティビティが再作成されるので状態をファイルに保存する必要はありません。
        // 構成変更以外の要因で停止する場合のみビューモデルの状態を保存して、随時保存が必要であるとマークします。
        if(!isChangingConfigurations) {
            for(RestorableViewModel viewModel : viewModelStore.values()) {
                viewModel.setSaveRequired(isSaveRequired);
                viewModel.saveState();
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    protected void onDestroy() {
        if(lifecycle != null) {
            lifecycle.removeObserver(this);
        }
        owner = null;
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
        if(owner == null) {
            throw new IllegalStateException("owner is null");
        }

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
                if(viewModelClass.isMemberClass() && !Modifier.isStatic(viewModelClass.getModifiers())) {
                    throw new NoSuchMethodException("Inner classes that extend RestorableViewModel must be static");
                }
                Constructor<T> constructor = viewModelClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                viewModel = constructor.newInstance();
                viewModel.initialize(file, restore, defaultState);
                viewModel.setSaveRequired(isSaveRequired);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch(IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch(InstantiationException e) {
                throw new RuntimeException(e);
            } catch(InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            viewModelStore.put(viewModelClass, viewModel);
        }
        return viewModel;
    }
}
