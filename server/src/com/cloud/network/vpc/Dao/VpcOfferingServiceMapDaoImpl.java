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
package com.cloud.network.vpc.Dao;

import java.util.List;

import javax.ejb.Local;

import com.cloud.network.Network.Service;
import com.cloud.network.vpc.VpcOfferingServiceMapVO;
import com.cloud.offerings.NetworkOfferingServiceMapVO;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

/**
 * @author Alena Prokharchyk
 */
@Local(value = VpcOfferingServiceMapDao.class)
@DB(txn = false)
public class VpcOfferingServiceMapDaoImpl extends GenericDaoBase<VpcOfferingServiceMapVO, Long> implements VpcOfferingServiceMapDao{
    final SearchBuilder<VpcOfferingServiceMapVO> AllFieldsSearch;
    final SearchBuilder<VpcOfferingServiceMapVO> MultipleServicesSearch;

    
    protected VpcOfferingServiceMapDaoImpl() {
        super();
        AllFieldsSearch = createSearchBuilder();
        AllFieldsSearch.and("vpcOffId", AllFieldsSearch.entity().getVpcOfferingId(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("service", AllFieldsSearch.entity().getService(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("provider", AllFieldsSearch.entity().getProvider(), SearchCriteria.Op.EQ);
        AllFieldsSearch.done();
        
        
        MultipleServicesSearch = createSearchBuilder();
        MultipleServicesSearch.and("vpcOffId", MultipleServicesSearch.entity().getVpcOfferingId(), SearchCriteria.Op.EQ);
        MultipleServicesSearch.and("service", MultipleServicesSearch.entity().getService(), SearchCriteria.Op.IN);
        MultipleServicesSearch.and("provider", MultipleServicesSearch.entity().getProvider(), SearchCriteria.Op.EQ);
        MultipleServicesSearch.done();
    }
    
    @Override
    public List<VpcOfferingServiceMapVO> listByVpcOffId(long vpcOffId) {
        SearchCriteria<VpcOfferingServiceMapVO> sc = AllFieldsSearch.create();
        sc.setParameters("vpcOffId", vpcOffId);
        return listBy(sc);
    }
    
    
    @Override
    public boolean areServicesSupportedByNetworkOffering(long networkOfferingId, Service... services) {
        SearchCriteria<VpcOfferingServiceMapVO> sc = MultipleServicesSearch.create();
        sc.setParameters("vpcOffId", networkOfferingId);
        
        if (services != null) {
            String[] servicesStr = new String[services.length];
            
            int i = 0;
            for (Service service : services) {
                servicesStr[i] = service.getName();
                i++;
            }
            
            sc.setParameters("service", (Object[])servicesStr);
        }
        
        List<VpcOfferingServiceMapVO> offeringServices = listBy(sc);
        
        if (services != null) {
            if (offeringServices.size() == services.length) {
                return true;
            }
        } else if (!offeringServices.isEmpty()) {
            return true;
        }
        
        return false;
    }
}
