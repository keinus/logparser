package org.keinus.logparser.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * String 키와 ArrayList<T> 값을 가지는 HashMap 유사 자료구조.
 * - null 키를 지원하며 별도로 관리합니다.
 * - get(key) 호출 시 해당 key의 값 리스트와 null 키의 값 리스트를 병합하여 반환합니다.
 *
 * @param <T> 리스트에 저장될 요소의 타입
 */
public class MergingHashMap<T> {

    // 일반 키(null이 아닌 키)와 값(ArrayList)을 저장하는 내부 맵
    private final Map<String, ArrayList<T>> internalMap;

    // null 키에 해당하는 값(ArrayList)을 저장하는 리스트
    private final ArrayList<T> nullKeyValueList;

    /**
     * MergingHashMap의 새 인스턴스를 생성합니다.
     */
    public MergingHashMap() {
        this.internalMap = new HashMap<>();
        this.nullKeyValueList = new ArrayList<>();
    }

    /**
     * 지정된 키에 지정된 값을 추가합니다.
     * 키가 null이면 null 키 전용 리스트에 값을 추가합니다.
     * 키가 null이 아니면 해당 키의 리스트에 값을 추가합니다. 리스트가 없으면 새로 생성합니다.
     *
     * @param key   값을 추가할 키 (null 가능)
     * @param value 추가할 값
     */
    public void put(String key, T value) {
        if (key == null) {
            this.nullKeyValueList.add(value);
        } else {
            this.internalMap.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
    }

    /**
     * 지정된 키와 연관된 값 리스트를 반환합니다.
     * 이 리스트는 지정된 키의 값들과 null 키의 값들이 병합된 결과입니다.
     * 만약 지정된 키가 맵에 없으면 null 키의 값들만 포함된 리스트가 반환됩니다.
     *
     * @param key 조회할 키 (null 가능)
     * @return 지정된 키의 값들과 null 키의 값들이 병합된 새로운 ArrayList.
     *         지정된 키와 null 키 모두에 값이 없으면 빈 리스트를 반환합니다.
     */
    public List<T> get(String key) {
        ArrayList<T> mergedList = new ArrayList<>();

        if (key != null) {
            List<T> specificList = this.internalMap.getOrDefault(key, new ArrayList<>());
            mergedList.addAll(specificList);
        }
        mergedList.addAll(this.nullKeyValueList);
        return mergedList;
    }

    /**
     * 지정된 키에 대한 매핑을 제거합니다.
     *
     * @param key 제거할 매핑의 키 (null 가능)
     * @return 제거되기 전 키와 연관된 값 리스트 (null 키의 값은 포함되지 않음).
     *         키가 null이면 null 키의 리스트가 비워지고 이전 리스트의 복사본이 반환됩니다.
     *         키가 없었으면 null을 반환합니다.
     */
    public List<T> remove(String key) {
        if (key == null) {
            if (nullKeyValueList.isEmpty()) {
                return nullKeyValueList;
            }
            ArrayList<T> previousNullList = new ArrayList<>(this.nullKeyValueList);
            this.nullKeyValueList.clear();
            return previousNullList;
        } else {
            return this.internalMap.remove(key);
        }
    }

     /**
     * 이 맵의 모든 값 리스트(null 키 포함)에서 지정된 값의 모든 인스턴스를 제거합니다.
     * ArrayList.remove(Object) 메소드를 사용하여 값을 비교하고 제거합니다.
     * 1. null 키의 리스트에서 값 제거
     * remove(Object)는 요소가 제거되면 true를 반환합니다.
     * 리스트 내에 동일한 값이 여러 개 있을 수 있으므로, 없어질 때까지 반복 제거합니다.
     * 2. internalMap의 모든 값(ArrayList) 리스트에서 값 제거
     * ConcurrentModificationException을 피하기 위해 map의 value 컬렉션을 직접 순회합니다.
     * 각 리스트 내부에서 remove(value)를 호출하는 것은 안전합니다.
     * @param value 제거할 값 (null 허용)
     * @return 이 호출의 결과로 하나 이상의 요소가 제거되었으면 true, 그렇지 않으면 false
     */
    public boolean removeValue(T value) {
        boolean removed = false;

        while (this.nullKeyValueList.remove(value)) {
            removed = true; // 하나라도 제거되면 true로 설정
        }

        for (ArrayList<T> list : this.internalMap.values()) {
            while (list.remove(value)) {
                removed = true; // 하나라도 제거되면 true로 설정
            }
        }
        return removed;
    }

    /**
     * 맵이 지정된 키를 포함하는지 여부를 반환합니다.
     *
     * @param key 확인할 키 (null 가능)
     * @return 키가 존재하면 true, 그렇지 않으면 false
     */
    public boolean containsKey(String key) {
        if (key == null) {
            return !this.nullKeyValueList.isEmpty();
        } else {
            return this.internalMap.containsKey(key);
        }
    }

    /**
     * 맵의 모든 매핑을 제거합니다.
     */
    public void clear() {
        this.internalMap.clear();
        this.nullKeyValueList.clear();
    }

    /**
     * 맵이 비어있는지 여부를 반환합니다. (null 키 포함)
     *
     * @return 일반 맵과 null 키 리스트가 모두 비어있으면 true, 아니면 false
     */
    public boolean isEmpty() {
        return this.internalMap.isEmpty() && this.nullKeyValueList.isEmpty();
    }

    /**
     * 맵의 키 개수(null 키 포함)를 반환합니다.
     *
     * @return 맵에 있는 키의 총 개수
     */
    public int size() {
        int size = this.internalMap.size();
        if (!this.nullKeyValueList.isEmpty()) {
            size++; // null 키도 하나의 키로 간주
        }
        return size;
    }
}