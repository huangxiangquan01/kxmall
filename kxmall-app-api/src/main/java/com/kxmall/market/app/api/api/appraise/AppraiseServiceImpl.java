package com.kxmall.market.app.api.api.appraise;

import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.kxmall.market.biz.service.appriaise.AppraiseBizService;
import com.kxmall.market.core.exception.ExceptionDefinition;
import com.kxmall.market.core.exception.AppServiceException;
import com.kxmall.market.core.exception.ServiceException;
import com.kxmall.market.data.component.CacheComponent;
import com.kxmall.market.data.domain.AppraiseDO;
import com.kxmall.market.data.domain.ImgDO;
import com.kxmall.market.data.domain.OrderDO;
import com.kxmall.market.data.domain.OrderSkuDO;
import com.kxmall.market.data.dto.appraise.AppraiseRequestItemDTO;
import com.kxmall.market.data.dto.appraise.AppraiseRequestDTO;
import com.kxmall.market.data.dto.appraise.AppraiseResponseDTO;
import com.kxmall.market.data.enums.BizType;
import com.kxmall.market.data.enums.OrderStatusType;
import com.kxmall.market.data.mapper.AppraiseMapper;
import com.kxmall.market.data.mapper.ImgMapper;
import com.kxmall.market.data.mapper.OrderMapper;
import com.kxmall.market.data.mapper.OrderSkuMapper;
import com.kxmall.market.data.model.Page;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;

/*
@author admin
@date  2019/7/6 - 11:08
*/
@Service
public class AppraiseServiceImpl implements AppraiseService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private ImgMapper imgMapper;
    @Autowired
    private AppraiseMapper appraiseMapper;
    @Autowired
    private OrderSkuMapper orderSkuMapper;
    @Autowired
    private CacheComponent cacheComponent;
    @Autowired
    private AppraiseBizService appraiseBizService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean addAppraise(AppraiseRequestDTO appraiseRequestDTO, Long userId) throws ServiceException {
        if (appraiseRequestDTO.getOrderId() == null) {
            throw new AppServiceException(ExceptionDefinition.APPRAISE_PARAM_CHECK_FAILED);
        }
        //??????????????????????????????????????????
        Integer integer = orderMapper.selectCount(
                new EntityWrapper<OrderDO>()
                        .eq("id", appraiseRequestDTO.getOrderId())
                        .eq("status", OrderStatusType.WAIT_APPRAISE.getCode())
                        .eq("user_id", userId));
        if (integer == 0) {
            throw new AppServiceException(ExceptionDefinition.APPRAISE_ORDER_CHECK_FAILED);
        }

        //??????????????????list???????????????????????????????????????????????????
        Date now = new Date();
        if (CollectionUtils.isEmpty(appraiseRequestDTO.getAppraiseDTOList())) {
            OrderDO orderDO = new OrderDO();
            orderDO.setStatus(OrderStatusType.COMPLETE.getCode());
            orderDO.setId(appraiseRequestDTO.getOrderId());
            orderDO.setGmtUpdate(now);
            orderMapper.updateById(orderDO);
        }

        //????????????????????????????????????????????????
        for (AppraiseRequestItemDTO appraiseDTO : appraiseRequestDTO.getAppraiseDTOList()) {
            Integer count = orderSkuMapper.selectCount(new EntityWrapper<OrderSkuDO>()
                    .eq("order_id", appraiseRequestDTO.getOrderId())
                    .eq("spu_id", appraiseDTO.getSpuId())
                    .eq("sku_id", appraiseDTO.getSkuId()));
            //???order_sku?????? ???????????????????????????????????????
            if (count == 0) {
                throw new AppServiceException(ExceptionDefinition.APPRAISE_PARAM_CHECK_FAILED);
            }

            AppraiseDO appraiseDO = new AppraiseDO();
            BeanUtils.copyProperties(appraiseDTO, appraiseDO);
            appraiseDO.setSpuId(appraiseDTO.getSpuId());
            appraiseDO.setId(null); //????????????id,???????????????????????????
            appraiseDO.setOrderId(appraiseRequestDTO.getOrderId()); //?????????????????????????????????DTO??????????????????
            appraiseDO.setUserId(userId);
            appraiseDO.setGmtCreate(now);
            appraiseDO.setGmtUpdate(appraiseDO.getGmtCreate());
            appraiseMapper.insert(appraiseDO);  //??????????????????????????????
            cacheComponent.delPrefixKey(AppraiseBizService.CA_APPRAISE_KEY + appraiseDO.getSpuId()); //????????????????????????
            if (appraiseDTO.getImgUrl() == null || appraiseDTO.getImgUrl().equals("")) {
                continue;
            }
            String imgUrlS = appraiseDTO.getImgUrl();
            String[] imgUrlList = imgUrlS.split(",");   //????????????
            for (String imgurl : imgUrlList) {
                ImgDO imgDO = new ImgDO();
                imgDO.setBizType(BizType.COMMENT.getCode());
                imgDO.setBizId(appraiseDO.getId());
                imgDO.setUrl(imgurl);
                imgDO.setGmtCreate(now);
                imgDO.setGmtUpdate(imgDO.getGmtCreate());
                imgMapper.insert(imgDO);
            }
        }

        //??????????????????
        OrderDO orderDO = new OrderDO();
        orderDO.setStatus(OrderStatusType.COMPLETE.getCode());
        orderDO.setId(appraiseRequestDTO.getOrderId());
        orderDO.setGmtUpdate(now);
        orderMapper.updateById(orderDO);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteAppraiseById(Long appraiseId, Long userId) throws ServiceException {
        Integer delete = appraiseMapper.delete(new EntityWrapper<AppraiseDO>()
                .eq("id", appraiseId)
                .eq("user_id", userId)); //????????????Id,??????Id
        if (delete > 0) {
            return true;
        } else {
            throw new AppServiceException(ExceptionDefinition.APPRAISE_PARAM_CHECK_FAILED);
        }
    }

    @Override
    public Page<AppraiseResponseDTO> getUserAllAppraise(Long userId, Integer pageNo, Integer pageSize) throws ServiceException {
        Integer count = appraiseMapper.selectCount(new EntityWrapper<AppraiseDO>().eq("user_id", userId));
        List<AppraiseResponseDTO> appraiseResponseDTOS = appraiseMapper.selectUserAllAppraise(userId, pageSize * (pageNo - 1), pageSize);
        for (AppraiseResponseDTO appraiseResponseDTO : appraiseResponseDTOS) {
            appraiseResponseDTO.setImgList(imgMapper.getImgs(BizType.COMMENT.getCode(), appraiseResponseDTO.getId()));
        }
        Page<AppraiseResponseDTO> page = new Page<>(appraiseResponseDTOS, pageNo, pageSize, count);
        return page;
    }


    @Override
    public Page<AppraiseResponseDTO> getSpuAllAppraise(Long spuId, Integer pageNo, Integer pageSize) throws ServiceException {
        return appraiseBizService.getSpuAllAppraise(spuId, pageNo, pageSize,1);
    }

    @Override
    public AppraiseResponseDTO getOneById(Long userId, Long appraiseId) throws ServiceException {
        AppraiseResponseDTO appraiseResponseDTO = appraiseMapper.selectOneById(appraiseId);
        if (appraiseResponseDTO == null) {
            throw new AppServiceException(ExceptionDefinition.APPRAISE_PARAM_CHECK_FAILED);
        }
        appraiseResponseDTO.setImgList(imgMapper.getImgs(BizType.COMMENT.getCode(), appraiseResponseDTO.getId()));

        return appraiseResponseDTO;
    }

    @Override
    public Boolean changeState(Long appraiseId) throws ServiceException {
        AppraiseDO appraiseDO = new AppraiseDO();
        appraiseDO.setId(appraiseId);
        appraiseDO.setState(1);
        return appraiseMapper.updateById(appraiseDO) > 0 ? true : false;
    }


}
