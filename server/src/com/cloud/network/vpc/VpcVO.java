// Copyright 2012 Citrix Systems, Inc. Licensed under the
// Apache License, Version 2.0 (the "License"); you may not use this
// file except in compliance with the License.  Citrix Systems, Inc.
// reserves all rights not expressly granted by the License.
// You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// 
// Automatically generated by addcopyright.py at 04/03/2012
package com.cloud.network.vpc;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;

import com.cloud.api.Identity;
import com.cloud.network.Network;
import com.cloud.network.Network.Service;
import com.cloud.utils.db.GenericDao;

/**
 * @author Alena Prokharchyk
 */

@Entity
@Table(name="vpc")
public class VpcVO implements Vpc, Identity {
    @Id
    @Column(name="id")
    long id;
    
    @Column(name="uuid")
    private String uuid;
    
    @Column(name="name")
    private String name;
    
    @Column(name = "display_text")
    String displayText;

    @Column(name="zone_id")
    long zoneId;

    @Column(name="cidr")
    private String cidr = null;
    
    @Column(name="domain_id")
    Long domainId = null;
    
    @Column(name="account_id")
    Long accountId = null;

    @Column(name="state")
    @Enumerated(value=EnumType.STRING)
    State state;
    
    @Column(name="vpc_offering_id")
    long vpcOfferingId;
    
    @Column(name=GenericDao.REMOVED_COLUMN)
    Date removed;

    @Column(name=GenericDao.CREATED_COLUMN)
    Date created;
    
    public VpcVO() {
        this.uuid = UUID.randomUUID().toString();
    }
    
    public VpcVO(long zoneId, String name, String displayText, long accountId, long domainId, long vpcOffId, String cidr) {
        this.zoneId = zoneId;
        this.name = name;
        this.displayText = displayText;
        this.accountId = accountId;
        this.domainId = domainId;
        this.cidr = cidr;
        this.uuid = UUID.randomUUID().toString();
        this.state = State.Enabled;
        this.vpcOfferingId = vpcOffId;
    }
    
    @Override
    public boolean readyToUse() {
        return state == State.Enabled;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getZoneId() {
        return zoneId;
    }

    @Override
    public String getCidr() {
        return cidr;
    }
    
    @Override
    public long getDomainId() {
        return domainId;
    }

    @Override
    public long getAccountId() {
        return accountId;
    }

    @Override
    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    @Override
    public long getVpcOfferingId() {
        return vpcOfferingId;
    }

    public Date getRemoved() {
        return removed;
    }

    @Override
    public String getDisplayText() {
        return displayText;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDisplayText(String displayText) {
        this.displayText = displayText;
    }

}
