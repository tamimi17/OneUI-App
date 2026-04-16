package com.example.oneuiapp.viewmodel;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.oneuiapp.data.entity.FontEntity;
import com.example.oneuiapp.data.repository.SystemFontRepository;

import java.util.List;

/**
 * SystemFontListViewModel - Fixed version
 * Smart loading: only shows spinner when data is initially empty
 */
public class SystemFontListViewModel extends AndroidViewModel {
    
    private static final String TAG = "SystemFontListViewModel";
    
    private final SystemFontRepository repository;
    private final LiveData<List<FontEntity>> fontsLiveData;
    private final MutableLiveData<Boolean> isLoadingLiveData;
    private final MutableLiveData<String> errorMessageLiveData;
    private final MutableLiveData<Boolean> isApiAvailableLiveData;
    
    public SystemFontListViewModel(@NonNull Application application) {
        super(application);
        
        repository = SystemFontRepository.getInstance(application);
        fontsLiveData = repository.getSystemFonts();
        isLoadingLiveData = new MutableLiveData<>(false);
        errorMessageLiveData = new MutableLiveData<>();
        isApiAvailableLiveData = new MutableLiveData<>(checkApiAvailability());
    }
    
    public LiveData<List<FontEntity>> getFontsLiveData() {
        return fontsLiveData;
    }
    
    public LiveData<Integer> getFontsCountLiveData() {
        return repository.getSystemFontsCount();
    }
    
    public LiveData<Boolean> getIsLoadingLiveData() {
        return isLoadingLiveData;
    }
    
    public LiveData<String> getErrorMessageLiveData() {
        return errorMessageLiveData;
    }
    
    public LiveData<Boolean> getIsApiAvailableLiveData() {
        return isApiAvailableLiveData;
    }
    
    private boolean checkApiAvailability() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }
    
    /**
     * FIXED: Smart loading - only show spinner when data is initially empty
     */
    public void loadSystemFonts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            errorMessageLiveData.postValue("SystemFonts API requires Android 10 (API 29) or higher");
            isApiAvailableLiveData.postValue(false);
            return;
        }
        
        // Check if we have existing data
        boolean hasExistingData = fontsLiveData.getValue() != null && !fontsLiveData.getValue().isEmpty();
        
        // Only show loading spinner if data is empty (first load)
        if (!hasExistingData) {
            isLoadingLiveData.postValue(true);
        }
        
        repository.loadAndSyncSystemFonts(new SystemFontRepository.OnSyncCompleteListener() {
            @Override
            public void onSyncComplete(int added, int updated, int deleted) {
                // Only hide spinner if we showed it (when data was empty)
                if (!hasExistingData) {
                    isLoadingLiveData.postValue(false);
                }
                
                String message = String.format("Synced: %d added, %d updated, %d deleted", 
                    added, updated, deleted);
                Log.d(TAG, message);
                
                if (added == 0 && updated == 0 && deleted == 0) {
                    Log.d(TAG, "No changes in system fonts");
                }
            }
        });
    }
    
    public LiveData<List<FontEntity>> searchFonts(String query) {
        if (query == null || query.trim().isEmpty()) {
            return fontsLiveData;
        }
        return repository.searchSystemFonts(query.trim());
    }
    
    public LiveData<List<FontEntity>> getSortedFonts(SystemFontRepository.SortType sortType, boolean ascending) {
        return repository.getSystemFontsSorted(sortType, ascending);
    }
    
    public void recordFontAccess(String fontPath) {
        if (fontPath != null && !fontPath.isEmpty()) {
            repository.recordAccess(fontPath);
        }
    }
    
    public void updateFontRealName(String fontPath, String realName) {
        if (fontPath != null && realName != null) {
            repository.updateRealName(fontPath, realName);
        }
    }
    
    public void refreshFonts() {
        isLoadingLiveData.postValue(true);
        
        repository.deleteAllSystemFonts(success -> {
            if (success) {
                loadSystemFonts();
            } else {
                isLoadingLiveData.postValue(false);
                errorMessageLiveData.postValue("Failed to clear old system fonts data");
            }
        });
    }
    
    public void getVariableFontsCount(SystemFontRepository.OnCountListener listener) {
        repository.getVariableFontsCount(listener);
    }
}
