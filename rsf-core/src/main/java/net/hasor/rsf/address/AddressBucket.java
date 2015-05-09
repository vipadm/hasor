/*
 * Copyright 2008-2009 the original 赵永春(zyc@hasor.net).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.hasor.rsf.address;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.hasor.rsf.address.route.flowcontrol.unit.UnitFlowControl;
import org.more.logger.LoggerHelper;
import org.more.util.StringUtils;
/**
 * 描述：用于接收地址更新同时也用来计算有效和无效地址。
 * 也负责提供服务地址列表集，负责分类存储和处理同一个服务的各种类型的服务地址数据，比如：
 * <ol>
 *  <li>同单元服务地址</li>
 *  <li>有效服务地址</li>
 *  <li>不可用服务地址</li>
 *  <li>全部服务地址</li>
 * </ol>
 * 所有对服务地址的进一 步处理都需要使用{@link #getAvailableAddresses()}获得的地址列表。
 * 如果应用了本地机房策略，则本地
 * @version : 2014年9月12日
 * @author 赵永春(zyc@hasor.net)
 */
public class AddressBucket {
    //原始数据
    private final String                       serviceID;         //服务ID
    private final String                       unitName;          //服务所属单元
    private final List<InterAddress>           allAddressList;    //所有备选地址
    private CopyOnWriteArrayList<InterAddress> invalidAddresses;  //不可用地址（可能包含本机房及其它机房的地址）
    private UnitFlowControl                    unitFlowControl;   //机房流控规则(单元划分)
    private final AddressPool                  forAddressPool;
    //
    //计算的可用地址
    private List<InterAddress>                 localUnitAddresses; //本单元地址
    private List<InterAddress>                 availableAddresses; //所有可用地址（包括本地单元）
    //
    //
    public AddressBucket(String serviceID, String unitName) {
        this.serviceID = serviceID;
        this.unitName = unitName;
        this.forAddressPool = null;
        this.allAddressList = new ArrayList<InterAddress>();
        this.invalidAddresses = new CopyOnWriteArrayList<InterAddress>();
        this.localUnitAddresses = new ArrayList<InterAddress>();
        this.availableAddresses = new ArrayList<InterAddress>();
        this.refreshAddress();
    }
    public AddressBucket(String serviceID, AddressPool forAddressPool) {
        this.serviceID = serviceID;
        this.unitName = forAddressPool.getUnitName();
        this.forAddressPool = forAddressPool;
        this.allAddressList = new ArrayList<InterAddress>();
        this.invalidAddresses = new CopyOnWriteArrayList<InterAddress>();
        this.localUnitAddresses = new ArrayList<InterAddress>();
        this.availableAddresses = new ArrayList<InterAddress>();
        this.refreshAddress();
    }
    //
    /**获取所有地址（包括本地的和无效的）。*/
    public synchronized List<InterAddress> getAllAddresses() {
        return new ArrayList<InterAddress>(this.allAddressList);
    }
    /**获取计算之后可用的地址。*/
    public synchronized List<InterAddress> getAvailableAddresses() {
        return new ArrayList<InterAddress>(availableAddresses);
    }
    /**失效地址。*/
    public synchronized List<InterAddress> getInvalidAddresses() {
        return new ArrayList<InterAddress>(invalidAddresses);
    }
    /**获取计算之后同一单元地址。*/
    public synchronized List<InterAddress> getLocalUnitAddresses() {
        return this.localUnitAddresses;
    }
    //
    /**新增地址支持动态新增*/
    public void newAddress(Collection<URI> newHostList) {
        if (newHostList == null || newHostList.isEmpty()) {
            LoggerHelper.logSevere("%s - newHostList is empty.", serviceID);
            return;
        }
        //
        List<InterAddress> newAddress = new ArrayList<InterAddress>();
        for (URI hostURI : newHostList) {
            boolean doAdd = true;
            InterAddress newHost = null;
            try {
                newHost = new InterAddress(hostURI);
                for (InterAddress hasAddress : this.allAddressList) {
                    if (newHost.equals(hasAddress) == true) {
                        doAdd = false;
                        break;
                    }
                }
            } catch (Throwable e) {
                LoggerHelper.logSevere("%s append new host '%s' format error.", serviceID, hostURI);
            }
            //
            if (doAdd) {
                newAddress.add(newHost);
            }
        }
        //
        this.allAddressList.addAll(newAddress);
        this.refreshAvailableAddress();
    }
    //
    /**将地址置为失效的。*/
    public void invalidAddress(InterAddress newInvalid) {
        for (InterAddress invalid : this.invalidAddresses) {
            String strInvalid = invalid.toString();
            String strInvalidNew = newInvalid.toString();
            if (StringUtils.equalsBlankIgnoreCase(strInvalid, strInvalidNew)) {
                return;
            }
        }
        try {//hashCode
            if (this.invalidAddresses.addIfAbsent(newInvalid)) {
                synchronized (this) {
                    refreshAvailableAddress();
                }
            }
        } catch (Exception e) {
            LoggerHelper.logWarn("invalid Address error.", e);
        }
    }
    /**强制刷新地址计算结果*/
    public void refreshAddress() {
        synchronized (this) {
            refreshAvailableAddress();
        }
    }
    //
    /**刷新地址*/
    private void refreshAvailableAddress() {
        if (this.forAddressPool != null) {
            //this.networkFlowControl = this.forAddressPool.getNetworkFlowControl();
            this.unitFlowControl = this.forAddressPool.getUnitFlowControl();
        }
        //
        //1.计算出有效的地址。
        List<InterAddress> availableList = new ArrayList<InterAddress>();
        for (InterAddress addressInfo : this.allAddressList) {
            boolean doAdd = true;
            for (InterAddress invalid : this.invalidAddresses) {
                if (addressInfo.equals(invalid)) {
                    doAdd = false;
                    break;
                }
            }
            if (doAdd) {
                availableList.add(addressInfo);//有效的
            }
        }
        //
        //2.机房单元化过滤
        List<InterAddress> unitList = availableList;
        if (this.unitFlowControl != null) {
            unitList = this.unitFlowControl.siftUnitAddress(unitName, availableList);
            if (unitList == null || unitList.isEmpty()) {
                unitList = availableList;
            }
            if (this.unitFlowControl.isLocalUnit(availableList.size(), unitList.size()) == false) {
                unitList = availableList;
            }
        }
        //
        //
        this.availableAddresses = availableList;
        this.localUnitAddresses = unitList;
    }
}