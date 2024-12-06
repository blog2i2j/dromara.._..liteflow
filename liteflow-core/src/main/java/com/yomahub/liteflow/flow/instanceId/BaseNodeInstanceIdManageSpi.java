package com.yomahub.liteflow.flow.instanceId;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.crypto.digest.MD5;
import com.yomahub.liteflow.flow.element.Chain;
import com.yomahub.liteflow.flow.element.Condition;
import com.yomahub.liteflow.flow.element.Node;
import com.yomahub.liteflow.flow.entity.InstanceInfoDto;
import org.apache.commons.lang.StringUtils;

import java.util.*;

import static com.yomahub.liteflow.util.JsonUtil.*;
import static com.yomahub.liteflow.util.SerialsUtil.generateShortUUID;

/**
 * @author lhh
 * @since 2.13.0
 */
public abstract class BaseNodeInstanceIdManageSpi implements NodeInstanceIdManageSpi {

    // 根据实例id获取 节点实例定位
    @Override
    public String getNodeInstanceLocationById(String chainId, String instanceId) {
        if (StringUtils.isBlank(chainId) || StringUtils.isBlank(instanceId)) {
            return "";
        }
        // 第一行为elMd5 第二行为实例id json格式信息
        List<String> instanceIdFile = readInstanceIdFile(chainId);

        for (int i = 1; i < instanceIdFile.size(); i++) {
            List<InstanceInfoDto> instanceInfos = parseList(instanceIdFile.get(i), InstanceInfoDto.class);

            for (InstanceInfoDto dto : instanceInfos) {
                if (Objects.equals(dto.getInstanceId(), instanceId)) {
                    return dto.getNodeId() + "(" + dto.getIndex() + ")";
                }
            }
        }

        return "";
    }

    // 往condition里设置instanceId
    @Override
    public void setNodesInstanceId(Condition condition, Chain chain) {
        NodeInstanceIdManageSpi nodeInstanceIdManageSpi = NodeInstanceIdManageSpiHolder.getInstance().getNodeInstanceIdManageSpi();

        String elMd5 = MD5.create().digestHex(chain.getEl());
        String chainId = chain.getChainId();
        List<String> instanceIdFile = nodeInstanceIdManageSpi.readInstanceIdFile(chainId);

        // 如果文件不存在，或者文件内容不是当前el，则写入
        if (CollUtil.isEmpty(instanceIdFile) || !instanceIdFile.get(0).equals(elMd5)) {
            nodeInstanceIdManageSpi.writeInstanceIdFile(writeNodeInstanceId(condition, chainId), elMd5, chainId);
        } else {
            // 文件存在，则直接读取
            List<InstanceInfoDto> instanceInfoDtos = new ArrayList<>();
            for (int i = 1; i < instanceIdFile.size(); i++) {
                List<InstanceInfoDto> instanceInfoDtos1 = parseList(instanceIdFile.get(i), InstanceInfoDto.class);
                if (instanceInfoDtos1 != null) {
                    instanceInfoDtos.addAll(instanceInfoDtos1);
                }
            }

            condition.getExecutableGroup().forEach((key, executables) -> {
                Map<String, Integer> idCntMap = new HashMap<>();
                executables.forEach(executable -> {
                    if (executable instanceof Node) {
                        Node node = (Node) executable;
                        idCntMap.put(node.getId(), idCntMap.getOrDefault(node.getId(), -1) + 1);

                        for (InstanceInfoDto dto : instanceInfoDtos) {
                            if (Objects.equals(dto.getNodeId(), node.getId())
                                    && Objects.equals(dto.getChainId(), node.getCurrChainId())
                                    && Objects.equals(dto.getIndex(), idCntMap.get(node.getId()))) {
                                node.setInstanceId(dto.getInstanceId());
                                break;
                            }
                        }

                    }
                });
            });
        }
    }

    // 写入时第一行为el的md5，第二行为json格式的groupKey和对应的nodeId 和实例id
    //          instanceId  a_XXX_0
    //         {"chainId":"chain1","nodeId":"a","instanceId":"XXXX","index":0},
    private List<InstanceInfoDto> writeNodeInstanceId(Condition condition, String chainId) {
        ArrayList<InstanceInfoDto> instanceInfos = new ArrayList<>();

        condition.getExecutableGroup().forEach((key, executables) -> {
            // 统计每个nodeId的索引
            Map<String, Integer> idCntMap = new HashMap<>();
            executables.forEach(executable -> {
                if (executable instanceof Node) {
                    Node node = (Node) executable;
                    InstanceInfoDto instanceInfoDto = new InstanceInfoDto();

                    instanceInfoDto.setChainId(chainId);
                    instanceInfoDto.setNodeId(node.getId());

                    String shortUUID = generateShortUUID();

                    idCntMap.put(node.getId(), idCntMap.getOrDefault(node.getId(), -1) + 1);

                    String instanceId = node.getId() + "_" + shortUUID + "_" + idCntMap.get(node.getId());

                    node.setInstanceId(instanceId);
                    instanceInfoDto.setInstanceId(instanceId);
                    instanceInfoDto.setIndex(idCntMap.get(node.getId()));

                    instanceInfos.add(instanceInfoDto);
                }
            });
        });

        return instanceInfos;
    }

}
