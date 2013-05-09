package org.ovirt.engine.core.vdsbroker.irsbroker;

import org.ovirt.engine.core.common.asynctasks.AsyncTaskCreationInfo;
import org.ovirt.engine.core.common.asynctasks.AsyncTaskType;
import org.ovirt.engine.core.common.vdscommands.DeleteImageGroupVDSCommandParameters;
import org.ovirt.engine.core.compat.Guid;

public class DeleteImageGroupVDSCommand<P extends DeleteImageGroupVDSCommandParameters> extends IrsCreateCommand<P> {
    public DeleteImageGroupVDSCommand(P parameters) {
        super(parameters);
    }

    @Override
    protected void ExecuteIrsBrokerCommand() {
        uuidReturn =
                getIrsProxy().deleteImage(getParameters().getStorageDomainId().toString(),
                                               getParameters().getStoragePoolId().toString(),
                                               getParameters().getImageGroupId().toString(),
                                               String.valueOf(getParameters().getPostZeros()),
                                               String.valueOf(getParameters().getForceDelete()).toLowerCase());

        ProceedProxyReturnValue();

        Guid taskID = new Guid(uuidReturn.mUuid);

        getVDSReturnValue().setCreationInfo(
                new AsyncTaskCreationInfo(taskID, AsyncTaskType.deleteImage, getParameters().getStoragePoolId()));
    }
}
