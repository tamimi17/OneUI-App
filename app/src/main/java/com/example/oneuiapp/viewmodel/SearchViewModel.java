package com.example.oneuiapp.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * SearchViewModel - إدارة حالة البحث عبر تغييرات Configuration
 * يحفظ نص البحث وحالة نشاط البحث بشكل مستقل عن دورة حياة Fragment
 * مطابق لنهج MainActivitySearchFragment في المكتبة الرسمية
 */
public class SearchViewModel extends ViewModel {
    
    private final MutableLiveData<String> searchQueryLiveData = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> isSearchActiveLiveData = new MutableLiveData<>(false);
    
    public LiveData<String> getSearchQueryLiveData() {
        return searchQueryLiveData;
    }
    
    public LiveData<Boolean> getIsSearchActiveLiveData() {
        return isSearchActiveLiveData;
    }
    
    public void setSearchQuery(String query) {
        String newQuery = query == null ? "" : query;
        searchQueryLiveData.setValue(newQuery);
    }
    
    public String getCurrentSearchQuery() {
        String query = searchQueryLiveData.getValue();
        return query == null ? "" : query;
    }
    
    public void activateSearch() {
        isSearchActiveLiveData.setValue(true);
    }
    
    public void deactivateSearch() {
        isSearchActiveLiveData.setValue(false);
        searchQueryLiveData.setValue("");
    }
    
    public boolean isSearchActive() {
        Boolean active = isSearchActiveLiveData.getValue();
        return active != null && active;
    }
}
