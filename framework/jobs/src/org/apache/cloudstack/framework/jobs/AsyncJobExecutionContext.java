// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.framework.jobs;

import org.apache.log4j.Logger;

import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.jobs.dao.AsyncJobJoinMapDao;
import org.apache.cloudstack.framework.jobs.impl.AsyncJobJoinMapVO;
import org.apache.cloudstack.framework.jobs.impl.JobSerializerHelper;
import org.apache.cloudstack.framework.jobs.impl.SyncQueueItem;
import org.apache.cloudstack.jobs.JobInfo;
import org.apache.cloudstack.managed.threadlocal.ManagedThreadLocal;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.user.Account;
import com.cloud.user.User;

public class AsyncJobExecutionContext  {
    private static final Logger s_logger = Logger.getLogger(AsyncJobExecutionContext.class);

    private AsyncJob _job;

    static private AsyncJobManager _jobMgr;
    static private AsyncJobJoinMapDao _joinMapDao;

    public static void init(AsyncJobManager jobMgr, AsyncJobJoinMapDao joinMapDao) {
        _jobMgr = jobMgr;
        _joinMapDao = joinMapDao;
    }

	private static ManagedThreadLocal<AsyncJobExecutionContext> s_currentExectionContext = new ManagedThreadLocal<AsyncJobExecutionContext>();

	public AsyncJobExecutionContext() {
	}

    public AsyncJobExecutionContext(AsyncJob job) {
		_job = job;
	}

	public SyncQueueItem getSyncSource() {
		return _job.getSyncSource();
	}

	public void resetSyncSource() {
		_job.setSyncSource(null);
	}

    public AsyncJob getJob() {
		return _job;
	}

    public void setJob(AsyncJob job) {
		_job = job;
	}

    public boolean isJobDispatchedBy(String jobDispatcherName) {
    	assert(jobDispatcherName != null);
    	if(_job != null && _job.getDispatcher() != null && _job.getDispatcher().equals(jobDispatcherName))
    		return true;

    	return false;
    }

    public void completeAsyncJob(JobInfo.Status jobStatus, int resultCode, String resultObject) {
    	assert(_job != null);
    	_jobMgr.completeAsyncJob(_job.getId(), jobStatus, resultCode, resultObject);
    }

    public void updateAsyncJobStatus(int processStatus, String resultObject) {
    	assert(_job != null);
    	_jobMgr.updateAsyncJobStatus(_job.getId(), processStatus, resultObject);
    }

    public void updateAsyncJobAttachment(String instanceType, Long instanceId) {
    	assert(_job != null);
    	_jobMgr.updateAsyncJobAttachment(_job.getId(), instanceType, instanceId);
    }

    public void logJobJournal(AsyncJob.JournalType journalType, String journalText, String journalObjJson) {
		assert(_job != null);
		_jobMgr.logJobJournal(_job.getId(), journalType, journalText, journalObjJson);
	}

    public void log(Logger logger, String journalText) {
        _jobMgr.logJobJournal(_job.getId(), AsyncJob.JournalType.SUCCESS, journalText, null);
        logger.debug(journalText);
    }

    public void joinJob(long joinJobId) {
    	assert(_job != null);
    	_jobMgr.joinJob(_job.getId(), joinJobId);
    }

    public void joinJob(long joinJobId, String wakeupHandler, String wakeupDispatcher,
    		String[] wakeupTopcisOnMessageBus, long wakeupIntervalInMilliSeconds, long timeoutInMilliSeconds) {
    	assert(_job != null);
    	_jobMgr.joinJob(_job.getId(), joinJobId, wakeupHandler, wakeupDispatcher, wakeupTopcisOnMessageBus,
    		wakeupIntervalInMilliSeconds, timeoutInMilliSeconds);
    }

    //
    // check failure exception before we disjoin the worker job, work job usually fails with exception
    // this will help propogate exception between jobs
	// TODO : it is ugly and this will become unnecessary after we switch to full-async mode
	//
    public void disjoinJob(long joinedJobId) throws InsufficientCapacityException,
		ConcurrentOperationException, ResourceUnavailableException {
    	assert(_job != null);

    	AsyncJobJoinMapVO record = _joinMapDao.getJoinRecord(_job.getId(), joinedJobId);
        _jobMgr.disjoinJob(_job.getId(), joinedJobId);

        if (record.getJoinStatus() == JobInfo.Status.FAILED) {
            if (record.getJoinResult() != null) {
                Object exception = JobSerializerHelper.fromObjectSerializedString(record.getJoinResult());
                if (exception != null && exception instanceof Exception) {
                    if (exception instanceof InsufficientCapacityException) {
                        s_logger.error("Job " + joinedJobId + " failed with InsufficientCapacityException");
                        throw (InsufficientCapacityException)exception;
                    }
                    else if (exception instanceof ConcurrentOperationException) {
                        s_logger.error("Job " + joinedJobId + " failed with ConcurrentOperationException");
                        throw (ConcurrentOperationException)exception;
                    }
                    else if (exception instanceof ResourceUnavailableException) {
                        s_logger.error("Job " + joinedJobId + " failed with ResourceUnavailableException");
                        throw (ResourceUnavailableException)exception;
                    }
                    else {
                        s_logger.error("Job " + joinedJobId + " failed with exception");
                        throw new RuntimeException((Exception)exception);
                    }
                }
            } else {
                s_logger.error("Job " + joinedJobId + " failed without providing an error object");
                throw new RuntimeException("Job " + joinedJobId + " failed without providing an error object");
            }
    	}
    }

    public void completeJoin(JobInfo.Status joinStatus, String joinResult) {
    	assert(_job != null);
    	_jobMgr.completeJoin(_job.getId(), joinStatus, joinResult);
    }

    public void completeJobAndJoin(JobInfo.Status joinStatus, String joinResult) {
    	assert(_job != null);
    	_jobMgr.completeJoin(_job.getId(), joinStatus, joinResult);
    	_jobMgr.completeAsyncJob(_job.getId(), joinStatus, 0, null);
    }

	public static AsyncJobExecutionContext getCurrentExecutionContext() {
		AsyncJobExecutionContext context = s_currentExectionContext.get();
        if (context == null) {
            // TODO, this has security implications, operations carried from API layer should always
            // set its context, otherwise, the fall-back here will use system security context
            //
            s_logger.warn("Job is executed without a context, setup psudo job for the executing thread");
            if (CallContext.current() != null)
                context = registerPseudoExecutionContext(CallContext.current().getCallingAccountId(),
                        CallContext.current().getCallingUserId());
            else
                context = registerPseudoExecutionContext(Account.ACCOUNT_ID_SYSTEM, User.UID_SYSTEM);
        }
		return context;
	}

    public static AsyncJobExecutionContext registerPseudoExecutionContext(long accountId, long userId) {
        AsyncJobExecutionContext context = s_currentExectionContext.get();
        if (context == null) {
            context = new AsyncJobExecutionContext();
            context.setJob(_jobMgr.getPseudoJob(accountId, userId));
            setCurrentExecutionContext(context);
        }

        return context;
    }

    public static AsyncJobExecutionContext unregister() {
        AsyncJobExecutionContext context = s_currentExectionContext.get();
        setCurrentExecutionContext(null);
        return context;
    }

    // This is intended to be package level access for AsyncJobManagerImpl only.
    public static void setCurrentExecutionContext(AsyncJobExecutionContext currentContext) {
		s_currentExectionContext.set(currentContext);
	}

    public static String getOriginJobContextId() {
        return String.valueOf(CallContext.current().getContextId());
    }
}
