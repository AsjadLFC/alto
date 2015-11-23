/*
 * Copyright © 2015 Yale University and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.alto.core.impl.networkmap.test;

import java.util.concurrent.Future;
import java.util.LinkedList;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;

import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.RoutedRpcRegistration;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Prefix;

import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.core.resourcepool.rev150921.AddResourceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.core.resourcepool.rev150921.AltoResourcepoolService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.core.resourcepool.rev150921.ResourcePool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.core.resourcepool.rev150921.ServiceContext;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.core.resourcepool.rev150921.resource.pool.Resource;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.core.resourcepool.rev150921.resource.pool.ResourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.core.resourcepool.rev150921.resource.pool.ResourceKey;

import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.core.types.rev150921.PidName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.core.types.rev150921.ResourceId;

import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.model.networkmap.rev151021.AddressTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.model.networkmap.rev151021.AddressTypeIpv4;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.model.networkmap.rev151021.AddressTypeIpv6;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.model.networkmap.rev151021.AltoModelNetworkmapService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.model.networkmap.rev151021.QueryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.model.networkmap.rev151021.QueryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.model.networkmap.rev151021.QueryOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.model.networkmap.rev151021.ResourceTypeNetworkmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.model.networkmap.rev151021.alto.request.networkmap.request.NetworkmapRequest;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.model.networkmap.rev151021.alto.response.networkmap.response.NetworkmapResponseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.model.networkmap.rev151021.networkmap.request.data.NetworkmapFilter;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.model.networkmap.rev151021.networkmap.response.data.NetworkMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.model.networkmap.rev151021.networkmap.response.data.network.map.Partition;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.model.networkmap.rev151021.networkmap.response.data.network.map.PartitionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.model.networkmap.rfc7285.rev151021.Ipv4PrefixList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.model.networkmap.rfc7285.rev151021.Ipv4PrefixListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.model.networkmap.rfc7285.rev151021.Ipv6PrefixList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.alto.service.model.networkmap.rfc7285.rev151021.Ipv6PrefixListBuilder;

import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AltoNetworkmapProvider implements BindingAwareProvider, AutoCloseable, AltoModelNetworkmapService {

    private static final Logger LOG = LoggerFactory.getLogger(AltoNetworkmapProvider.class);

    private DataBroker m_dataBrokerService = null;
    private RoutedRpcRegistration<AltoModelNetworkmapService> m_serviceReg = null;
    private AltoResourcepoolService m_resourcepoolService = null;

    private static final ResourceId TEST_NETWORKMAP_RID = new ResourceId("test-model-networkmap");
    private InstanceIdentifier<Resource> m_testIID = null;

    protected InstanceIdentifier<Resource> getResourceIID(ResourceId rid) {
        ResourceKey key = new ResourceKey(rid);
        return InstanceIdentifier.builder(ResourcePool.class).child(Resource.class, key).build();
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("AltoModelNetworkProvider Session Initiated");

        m_dataBrokerService = session.getSALService(DataBroker.class);
        m_serviceReg = session.addRoutedRpcImplementation(AltoModelNetworkmapService.class, this);

        ResourceBuilder builder = new ResourceBuilder();
        builder.setResourceId(TEST_NETWORKMAP_RID).setType(ResourceTypeNetworkmap.class);

        AddResourceInputBuilder inputBuilder = new AddResourceInputBuilder();
        inputBuilder.fieldsFrom(builder.build());

        try {
            AltoResourcepoolService resourcepool;
            resourcepool = session.getRpcService(AltoResourcepoolService.class);

            RpcResult<Void> result;
            result = resourcepool.addResource(inputBuilder.build()).get();

            assert result.isSuccessful();

            m_testIID = getResourceIID(TEST_NETWORKMAP_RID);
            m_serviceReg.registerPath(ServiceContext.class, m_testIID);
        } catch (Exception e) {
        }
    }

    @Override
    public void close() throws Exception {
        LOG.info("AltoModelBaseProvider Closed");
    }

    @Override
    public Future<RpcResult<QueryOutput>> query(QueryInput input) {
        if (!input.getType().equals(ResourceTypeNetworkmap.class)) {
            return RpcResultBuilder.<QueryOutput>failed().buildFuture();
        }
        NetworkmapRequest request = (NetworkmapRequest)input.getRequest();
        NetworkmapFilter filter = request.getNetworkmapFilter();

        List<PidName> pids = filter.getPid();
        List<Class<? extends AddressTypeBase>> types = filter.getAddressType();

        List<Partition> partitionList = new LinkedList<>();
        int index = 0;
        for (PidName pid: pids) {
            ++index;

            PartitionBuilder partitionBuilder = new PartitionBuilder();
            partitionBuilder.setPid(pid);

            if (types.contains(AddressTypeIpv4.class)) {
                LinkedList<Ipv4Prefix> ipv4List = new LinkedList<Ipv4Prefix>();
                ipv4List.add(new Ipv4Prefix("192.168." + index + ".0/24"));

                Ipv4PrefixListBuilder v4Builder = new Ipv4PrefixListBuilder();
                v4Builder.setIpv4(ipv4List);

                partitionBuilder.addAugmentation(Ipv4PrefixList.class, v4Builder.build());
            }
            if (types.contains(AddressTypeIpv6.class)) {
                LinkedList<Ipv6Prefix> ipv6List = new LinkedList<Ipv6Prefix>();
                ipv6List.add(new Ipv6Prefix("2001:b8:ca2:" + index + "::0/64"));

                Ipv6PrefixListBuilder v6Builder = new Ipv6PrefixListBuilder();
                v6Builder.setIpv6(ipv6List);

                partitionBuilder.addAugmentation(Ipv6PrefixList.class, v6Builder.build());
            }

            partitionList.add(partitionBuilder.build());
        }

        NetworkMapBuilder nmBuilder = new NetworkMapBuilder();
        nmBuilder.setPartition(partitionList);

        NetworkmapResponseBuilder nmrBuilder = new NetworkmapResponseBuilder();
        nmrBuilder.setNetworkMap(nmBuilder.build());

        QueryOutputBuilder builder = new QueryOutputBuilder();

        builder.setType(ResourceTypeNetworkmap.class).setResponse(nmrBuilder.build());
        return RpcResultBuilder.<QueryOutput>success(builder.build()).buildFuture();
    }

}