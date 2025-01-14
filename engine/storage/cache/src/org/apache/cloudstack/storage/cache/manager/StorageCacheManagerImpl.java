/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.storage.cache.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import org.apache.cloudstack.engine.subsystem.api.storage.CopyCommandResult;
import org.apache.cloudstack.engine.subsystem.api.storage.DataMotionService;
import org.apache.cloudstack.engine.subsystem.api.storage.DataObject;
import org.apache.cloudstack.engine.subsystem.api.storage.DataObjectInStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreManager;
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine;
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine.Event;
import org.apache.cloudstack.engine.subsystem.api.storage.Scope;
import org.apache.cloudstack.engine.subsystem.api.storage.StorageCacheManager;
import org.apache.cloudstack.framework.async.AsyncCallFuture;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.cloudstack.storage.cache.allocator.StorageCacheAllocator;
import org.apache.cloudstack.storage.datastore.ObjectInDataStoreManager;
import org.apache.cloudstack.storage.datastore.db.ImageStoreVO;

import com.cloud.configuration.Config;
import com.cloud.storage.DataStoreRole;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.component.Manager;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.db.QueryBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.exception.CloudRuntimeException;

public class StorageCacheManagerImpl implements StorageCacheManager, Manager {
    private static final Logger s_logger = Logger.getLogger(StorageCacheManagerImpl.class);
    @Inject
    List<StorageCacheAllocator> storageCacheAllocator;
    @Inject
    DataMotionService dataMotionSvr;
    @Inject
    ObjectInDataStoreManager objectInStoreMgr;
    @Inject
    DataStoreManager dataStoreManager;
    @Inject
    StorageCacheReplacementAlgorithm cacheReplacementAlgorithm;
    @Inject
    ConfigurationDao configDao;
    Boolean cacheReplacementEnabled = Boolean.TRUE;
    int workers;
    ScheduledExecutorService executors;
    int cacheReplaceMentInterval;

    @Override
    public DataStore getCacheStorage(Scope scope) {
        for (StorageCacheAllocator allocator : storageCacheAllocator) {
            DataStore store = allocator.getCacheStore(scope);
            if (store != null) {
                return store;
            }
        }
        return null;
    }

    
    @Override
    public DataStore getCacheStorage(DataObject data, Scope scope) {
        for (StorageCacheAllocator allocator : storageCacheAllocator) {
            DataStore store = allocator.getCacheStore(data, scope);
            if (store != null) {
                return store;
            }
        }
        return null;
    }


    protected List<DataStore> getCacheStores() {
        QueryBuilder<ImageStoreVO> sc = QueryBuilder.create(ImageStoreVO.class);
        sc.and(sc.entity().getRole(), SearchCriteria.Op.EQ,DataStoreRole.ImageCache);
        List<ImageStoreVO> imageStoreVOs = sc.list();
        List<DataStore> stores = new ArrayList<DataStore>();
        for (ImageStoreVO vo : imageStoreVOs) {
            stores.add(dataStoreManager.getDataStore(vo.getId(), vo.getRole()));
        }
        return stores;
    }

    @Override
    public String getName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setName(String name) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setConfigParams(Map<String, Object> params) {
        // TODO Auto-generated method stub

    }

    @Override
    public Map<String, Object> getConfigParams() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getRunLevel() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setRunLevel(int level) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        cacheReplacementEnabled = Boolean.parseBoolean(configDao.getValue(Config.StorageCacheReplacementEnabled.key()));
        cacheReplaceMentInterval = NumbersUtil.parseInt(configDao.getValue(Config.StorageCacheReplacementInterval.key()), 86400);
        workers = NumbersUtil.parseInt(configDao.getValue(Config.ExpungeWorkers.key()), 10);
        executors = Executors.newScheduledThreadPool(workers, new NamedThreadFactory("StorageCacheManager-cache-replacement"));
        return true;
    }

    protected class CacheReplacementRunner extends ManagedContextRunnable {

        @Override
        protected void runInContext() {
            GlobalLock replacementLock = null;
            try {
                replacementLock = GlobalLock.getInternLock("storageCacheMgr.replacement");
                if (replacementLock.lock(3)) {
                    List<DataStore> stores = getCacheStores();
                    Collections.shuffle(stores);
                    DataObject object = null;
                    DataStore findAStore = null;
                    for (DataStore store : stores) {
                        object = cacheReplacementAlgorithm.chooseOneToBeReplaced(store);
                        findAStore = store;
                        if (object != null) {
                            break;
                        }
                    }

                    if (object == null) {
                        return;
                    }

                    while(object != null) {
                        object.delete();
                        object = cacheReplacementAlgorithm.chooseOneToBeReplaced(findAStore);
                    }
                }
            } catch (Exception e) {
                s_logger.debug("Failed to execute CacheReplacementRunner: " + e.toString());
            } finally {
                if (replacementLock != null) {
                    replacementLock.unlock();
                }
            }
        }
    }

    @Override
    public boolean start() {
        if (cacheReplacementEnabled) {
            Random generator = new Random();
            int initalDelay = generator.nextInt(cacheReplaceMentInterval);
            executors.scheduleWithFixedDelay(new CacheReplacementRunner(), initalDelay, cacheReplaceMentInterval, TimeUnit.SECONDS);
        }
        return true;
    }

    @Override
    public boolean stop() {
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    public DataObject createCacheObject(DataObject data, DataStore store) {
        DataObjectInStore obj = objectInStoreMgr.findObject(data, store);
        if (obj != null && obj.getState() == ObjectInDataStoreStateMachine.State.Ready) {
            s_logger.debug("there is already one in the cache store");
            DataObject dataObj = objectInStoreMgr.get(data, store);
            dataObj.incRefCount();
            return dataObj;
        }

        DataObject objOnCacheStore = store.create(data);

        AsyncCallFuture<CopyCommandResult> future = new AsyncCallFuture<CopyCommandResult>();
        CopyCommandResult result = null;
        try {
            objOnCacheStore.processEvent(Event.CreateOnlyRequested);

            dataMotionSvr.copyAsync(data, objOnCacheStore, future);
            result = future.get();

            if (result.isFailed()) {
                objOnCacheStore.processEvent(Event.OperationFailed);
            } else {
                objOnCacheStore.processEvent(Event.OperationSuccessed, result.getAnswer());
                objOnCacheStore.incRefCount();
                return objOnCacheStore;
            }
        } catch (InterruptedException e) {
            s_logger.debug("create cache storage failed: " + e.toString());
            throw new CloudRuntimeException(e);
        } catch (ExecutionException e) {
            s_logger.debug("create cache storage failed: " + e.toString());
            throw new CloudRuntimeException(e);
        } finally {
            if (result == null) {
                objOnCacheStore.processEvent(Event.OperationFailed);
            }
        }
        return null;
    }

    @Override
    public DataObject createCacheObject(DataObject data, Scope scope) {
        DataStore cacheStore = getCacheStorage(scope);

        if (cacheStore == null)
        {
            String errMsg = "No cache DataStore in scope id " + scope.getScopeId() + " type " + scope.getScopeType().toString();
            throw new CloudRuntimeException(errMsg);
        }
        return this.createCacheObject(data, cacheStore);
    }

    @Override
    public DataObject getCacheObject(DataObject data, Scope scope) {
        DataStore cacheStore = getCacheStorage(scope);
        DataObject objOnCacheStore = cacheStore.create(data);
        objOnCacheStore.incRefCount();
        return objOnCacheStore;
    }

    @Override
    public boolean releaseCacheObject(DataObject data) {
        data.decRefCount();
        return true;
    }

    @Override
    public boolean deleteCacheObject(DataObject data) {
        return data.getDataStore().delete(data);
    }
}
