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
package com.cloud.vm;

import java.util.Date;
import java.util.Map;

import org.apache.cloudstack.acl.ControlledEntity;
import org.apache.cloudstack.api.Identity;
import org.apache.cloudstack.api.InternalIdentity;

import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.utils.fsm.StateMachine2;
import com.cloud.utils.fsm.StateObject;

/**
 * VirtualMachine describes the properties held by a virtual machine
 *
 */
public interface VirtualMachine extends RunningOn, ControlledEntity, Identity, InternalIdentity, StateObject<VirtualMachine.State> {

    public enum PowerState {
        PowerUnknown, PowerOn, PowerOff,
    }

    public enum State {
        Starting(true, "VM is being started.  At this state, you should find host id filled which means it's being started on that host."), Running(
                false,
                "VM is running.  host id has the host that it is running on."), Stopping(true, "VM is being stopped.  host id has the host that it is being stopped on."), Stopped(
                false,
                "VM is stopped.  host id should be null."), Destroyed(false, "VM is marked for destroy."), Expunging(true, "VM is being   expunged."), Migrating(
                true,
                "VM is being migrated.  host id holds to from host"), Error(false, "VM is in error"), Unknown(false, "VM state is unknown."), Shutdowned(
                false,
                "VM is shutdowned from inside");

        private final boolean _transitional;
        String _description;

        private State(boolean transitional, String description) {
            _transitional = transitional;
            _description = description;
        }

        public String getDescription() {
            return _description;
        }

        public boolean isTransitional() {
            return _transitional;
        }

        public static StateMachine2<State, VirtualMachine.Event, VirtualMachine> getStateMachine() {
            return s_fsm;
        }

        protected static final StateMachine2<State, VirtualMachine.Event, VirtualMachine> s_fsm = new StateMachine2<State, VirtualMachine.Event, VirtualMachine>();
        static {
            s_fsm.addTransition(State.Stopped, VirtualMachine.Event.StartRequested, State.Starting);
            s_fsm.addTransition(State.Stopped, VirtualMachine.Event.DestroyRequested, State.Destroyed);
            s_fsm.addTransition(State.Stopped, VirtualMachine.Event.StopRequested, State.Stopped);
            s_fsm.addTransition(State.Stopped, VirtualMachine.Event.AgentReportStopped, State.Stopped);

            // please pay attention about state transition to Error state, there should be only one case (failed in VM
            // creation process)
            // that can have such transition
            s_fsm.addTransition(State.Stopped, VirtualMachine.Event.OperationFailedToError, State.Error);

            s_fsm.addTransition(State.Stopped, VirtualMachine.Event.OperationFailed, State.Stopped);
            s_fsm.addTransition(State.Stopped, VirtualMachine.Event.ExpungeOperation, State.Expunging);
            s_fsm.addTransition(State.Stopped, VirtualMachine.Event.AgentReportShutdowned, State.Stopped);
            s_fsm.addTransition(State.Stopped, VirtualMachine.Event.StorageMigrationRequested, State.Migrating);
            s_fsm.addTransition(State.Starting, VirtualMachine.Event.OperationRetry, State.Starting);
            s_fsm.addTransition(State.Starting, VirtualMachine.Event.OperationSucceeded, State.Running);
            s_fsm.addTransition(State.Starting, VirtualMachine.Event.OperationFailed, State.Stopped);
            s_fsm.addTransition(State.Starting, VirtualMachine.Event.AgentReportRunning, State.Running);
            s_fsm.addTransition(State.Starting, VirtualMachine.Event.AgentReportStopped, State.Stopped);
            s_fsm.addTransition(State.Starting, VirtualMachine.Event.AgentReportShutdowned, State.Stopped);
            s_fsm.addTransition(State.Destroyed, VirtualMachine.Event.RecoveryRequested, State.Stopped);
            s_fsm.addTransition(State.Destroyed, VirtualMachine.Event.ExpungeOperation, State.Expunging);
            s_fsm.addTransition(State.Running, VirtualMachine.Event.MigrationRequested, State.Migrating);
            s_fsm.addTransition(State.Running, VirtualMachine.Event.AgentReportRunning, State.Running);
            s_fsm.addTransition(State.Running, VirtualMachine.Event.AgentReportStopped, State.Stopped);
            s_fsm.addTransition(State.Running, VirtualMachine.Event.StopRequested, State.Stopping);
            s_fsm.addTransition(State.Running, VirtualMachine.Event.AgentReportShutdowned, State.Stopped);
            s_fsm.addTransition(State.Running, VirtualMachine.Event.AgentReportMigrated, State.Running);
            s_fsm.addTransition(State.Migrating, VirtualMachine.Event.MigrationRequested, State.Migrating);
            s_fsm.addTransition(State.Migrating, VirtualMachine.Event.OperationSucceeded, State.Running);
            s_fsm.addTransition(State.Migrating, VirtualMachine.Event.OperationFailed, State.Running);
            s_fsm.addTransition(State.Migrating, VirtualMachine.Event.AgentReportRunning, State.Running);
            s_fsm.addTransition(State.Migrating, VirtualMachine.Event.AgentReportStopped, State.Stopped);
            s_fsm.addTransition(State.Migrating, VirtualMachine.Event.AgentReportShutdowned, State.Stopped);
            s_fsm.addTransition(State.Stopping, VirtualMachine.Event.OperationSucceeded, State.Stopped);
            s_fsm.addTransition(State.Stopping, VirtualMachine.Event.OperationFailed, State.Running);
            s_fsm.addTransition(State.Stopping, VirtualMachine.Event.AgentReportRunning, State.Running);
            s_fsm.addTransition(State.Stopping, VirtualMachine.Event.AgentReportStopped, State.Stopped);
            s_fsm.addTransition(State.Stopping, VirtualMachine.Event.StopRequested, State.Stopping);
            s_fsm.addTransition(State.Stopping, VirtualMachine.Event.AgentReportShutdowned, State.Stopped);
            s_fsm.addTransition(State.Expunging, VirtualMachine.Event.OperationFailed, State.Expunging);
            s_fsm.addTransition(State.Expunging, VirtualMachine.Event.ExpungeOperation, State.Expunging);
            s_fsm.addTransition(State.Error, VirtualMachine.Event.DestroyRequested, State.Expunging);
            s_fsm.addTransition(State.Error, VirtualMachine.Event.ExpungeOperation, State.Expunging);

            s_fsm.addTransition(State.Stopping, VirtualMachine.Event.FollowAgentPowerOnReport, State.Running);
            s_fsm.addTransition(State.Stopped, VirtualMachine.Event.FollowAgentPowerOnReport, State.Running);
            s_fsm.addTransition(State.Running, VirtualMachine.Event.FollowAgentPowerOnReport, State.Running);
            s_fsm.addTransition(State.Migrating, VirtualMachine.Event.FollowAgentPowerOnReport, State.Running);
            s_fsm.addTransition(State.Starting, VirtualMachine.Event.FollowAgentPowerOffReport, State.Stopped);
            s_fsm.addTransition(State.Stopping, VirtualMachine.Event.FollowAgentPowerOffReport, State.Stopped);
            s_fsm.addTransition(State.Running, VirtualMachine.Event.FollowAgentPowerOffReport, State.Stopped);
            s_fsm.addTransition(State.Migrating, VirtualMachine.Event.FollowAgentPowerOffReport, State.Stopped);
        }

        public static boolean isVmStarted(State oldState, Event e, State newState) {
            if (oldState == State.Starting && newState == State.Running) {
                return true;
            }
            return false;
        }

        public static boolean isVmStopped(State oldState, Event e, State newState) {
            if (oldState == State.Stopping && newState == State.Stopped) {
                return true;
            }
            return false;
        }

        public static boolean isVmMigrated(State oldState, Event e, State newState) {
            if (oldState == State.Migrating && newState == State.Running && (e == Event.OperationSucceeded || e == Event.AgentReportRunning)) {
                return true;
            }
            return false;
        }

        public static boolean isVmCreated(State oldState, Event e, State newState) {
            if (oldState == State.Destroyed && newState == State.Stopped) {
                // VM recover
                return true;
            }
            return false;
        }

        public static boolean isVmDestroyed(State oldState, Event e, State newState) {
            if (oldState == State.Stopped && newState == State.Destroyed) {
                return true;
            }
            if (oldState == State.Stopped && newState == State.Error) {
                return true;
            }

            if (oldState == State.Stopped && newState == State.Expunging) {
                return true;
            }

            return false;
        }
    }

    static final String IsDynamicScalingEnabled = "enable.dynamic.scaling";

    public enum Event {
        CreateRequested,
        StartRequested,
        StopRequested,
        DestroyRequested,
        RecoveryRequested,
        AgentReportStopped,
        AgentReportRunning,
        MigrationRequested,
        StorageMigrationRequested,
        ExpungeOperation,
        OperationSucceeded,
        OperationFailed,
        OperationFailedToError,
        OperationRetry,
        AgentReportShutdowned,
        AgentReportMigrated,
        RevertRequested,
        SnapshotRequested,

        // added for new VMSync logic
        FollowAgentPowerOnReport,
        FollowAgentPowerOffReport,
    };

    public enum Type {
        User(false), DomainRouter(true), ConsoleProxy(true), SecondaryStorageVm(true), ElasticIpVm(true), ElasticLoadBalancerVm(true), InternalLoadBalancerVm(true),

        /*
         * UserBareMetal is only used for selecting VirtualMachineGuru, there is no
         * VM with this type. UserBareMetal should treat exactly as User.
         */
        UserBareMetal(false);

        boolean _isUsedBySystem;

        private Type(boolean isUsedBySystem) {
            _isUsedBySystem = isUsedBySystem;
        }

        public boolean isUsedBySystem() {
            return _isUsedBySystem;
        }
    }

    /**
     * @return The name of the vm instance used by the cloud stack to uniquely
     *         reference this VM. You can build names that starts with this name and it
     *         guarantees uniqueness for things related to the VM.
     */
    String getInstanceName();

    /**
     * @return the host name of the virtual machine. If the user did not
     *         specify the host name when creating the virtual machine then it is
     *         defaults to the instance name.
     */
    String getHostName();

    /**
     * @return the ip address of the virtual machine.
     */
    String getPrivateIpAddress();

    /**
     * @return mac address.
     */
    String getPrivateMacAddress();

    /**
     * @return password of the host for vnc purposes.
     */
    String getVncPassword();

    /**
     * @return the state of the virtual machine
     */
    // State getState();

    /**
     * @return template id.
     */
    long getTemplateId();

    /**
     * returns the guest OS ID
     *
     * @return guestOSId
     */
    long getGuestOSId();

    /**
     * @return pod id.
     */
    Long getPodIdToDeployIn();

    /**
     * @return data center id.
     */
    long getDataCenterId();

    /**
     * @return id of the host it was assigned last time.
     */
    Long getLastHostId();

    @Override
    Long getHostId();

    /**
     * @return should HA be enabled for this machine?
     */
    boolean isHaEnabled();

    /**
     * @return should limit CPU usage to the service offering?
     */
    boolean limitCpuUse();

    /**
     * @return date when machine was created
     */
    Date getCreated();

    long getServiceOfferingId();

    Long getDiskOfferingId();

    Type getType();

    HypervisorType getHypervisorType();

    Map<String, String> getDetails();

    long getUpdated();

}
