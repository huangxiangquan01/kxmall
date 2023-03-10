package com.kxmall.market.admin.api.api.goodsinstock;


import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.kxmall.market.core.exception.AdminServiceException;
import com.kxmall.market.core.exception.ExceptionDefinition;
import com.kxmall.market.core.exception.ServiceException;
import com.kxmall.market.data.domain.GoodsInStockDO;
import com.kxmall.market.data.domain.InStockSpuDO;
import com.kxmall.market.data.domain.StorageDO;
import com.kxmall.market.data.dto.GoodsInStockDTO;
import com.kxmall.market.data.enums.GoodsInStockType;
import com.kxmall.market.data.enums.StorageStatusType;
import com.kxmall.market.data.mapper.GoodsInStockMapper;
import com.kxmall.market.data.mapper.InStockSpuMapper;
import com.kxmall.market.data.mapper.StockMapper;
import com.kxmall.market.data.mapper.StorageMapper;
import com.kxmall.market.data.model.Page;
import org.apache.commons.collections.CollectionUtils;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Service
public class GoodsInStockServiceImpl implements GoodsInStockService {

    @Resource
    private GoodsInStockMapper goodsInStockMapper;

    @Resource
    private StockMapper stockMapper;

    @Resource
    private InStockSpuMapper inStockSpuMapper;

    @Resource
    private StorageMapper storageMapper;


    @Override
    public Page<GoodsInStockDO> list(Long storageId, String inStockNumbers, Integer states, String ingoingDay, Integer page, Integer limit, Long adminId) throws ServiceException {
        Wrapper<GoodsInStockDO> wrapper = new EntityWrapper<>();
        if (!StringUtils.isEmpty(storageId)) {
            wrapper.eq("storageId", storageId);
        }
        if (inStockNumbers != null) {
            wrapper.like("inStockNumbers", inStockNumbers);
        }
        if (states != null) {
            wrapper.eq("states", states);
        }
        if (!StringUtils.isEmpty(ingoingDay)) {
            wrapper.like("ingoingTime", ingoingDay);
        }
        wrapper.orderBy("gmtUpdate", false);
        List<GoodsInStockDO> goodsInStockDOS = goodsInStockMapper.selectPage(new RowBounds((page - 1) * limit, limit), wrapper);
        Integer count = goodsInStockMapper.selectCount(wrapper);
        return new Page<>(goodsInStockDOS, page, limit, count);
    }

    @Override
    public GoodsInStockDTO selectById(String InStockNumbers, Long id, Long adminId) throws ServiceException {
        Wrapper<InStockSpuDO> wrapper = new EntityWrapper<>();
        wrapper.like("in_stock_numbers", InStockNumbers);
        List<InStockSpuDO> inStockSpuDOS = inStockSpuMapper.selectList(wrapper);
        GoodsInStockDO goodsInStockDO = goodsInStockMapper.selectById(id);
        GoodsInStockDTO goodsInStockDTO = new GoodsInStockDTO();
        goodsInStockDTO.setInStockSpuDOS(inStockSpuDOS);
        BeanUtils.copyProperties(goodsInStockDO, goodsInStockDTO);
        return goodsInStockDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GoodsInStockDTO create(GoodsInStockDTO goodsInStockDTO, Long adminId) throws ServiceException {
        //????????????????????????,O+?????????+?????????
        //????????????????????????????????????
        GoodsInStockDO goodsInStockDO1 = goodsInStockMapper.selectByMax();
        String max_code = "";//?????????????????????????????????
        String in_skock = "";//???????????????????????????
        if (goodsInStockDO1 != null) {
            max_code = goodsInStockDO1.getInStockNumbers();
        }
        //???????????????????????????
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
        String uid_pfix = simpleDateFormat.format(new Date());
        //??????????????????????????????
        if (max_code != null && max_code.contains(uid_pfix)) {
            String uid_end = max_code.substring(9, 14);
            Integer endNum = Integer.parseInt(uid_end);
            //100001
            endNum = 100000 + endNum + 1;
            String num = endNum + "";
            //??????100001????????????1
            String numm = num.substring(1);
            in_skock = "I" + uid_pfix + numm;
        } else {
            //?????????????????????
            in_skock = "I" + uid_pfix + "00001";
        }
        //???????????????????????????
        List<InStockSpuDO> inStockSpuDOS = goodsInStockDTO.getInStockSpuDOS();
        if (!CollectionUtils.isEmpty(inStockSpuDOS)) {
            for (InStockSpuDO inStockSpuDO : inStockSpuDOS) {
                inStockSpuDO.setInStockNumbers(in_skock);
                if (inStockSpuMapper.insert(inStockSpuDO) <= 0) {
                    throw new AdminServiceException(ExceptionDefinition.GOODS_IN_INSERT);
                }
            }
        }
        //????????????
        GoodsInStockDO goodsInStockDO = new GoodsInStockDO();
        BeanUtils.copyProperties(goodsInStockDTO, goodsInStockDO);
        goodsInStockDO.setInStockNumbers(in_skock);
        goodsInStockDO.setStates(GoodsInStockType.TO_BE_FOR_STOCK.getCode());
        goodsInStockDO.setGmtUpdate(new Date());
        if (goodsInStockMapper.insert(goodsInStockDO) <= 0) {
            throw new AdminServiceException(ExceptionDefinition.ADMIN_UNKNOWN_EXCEPTION);
        }
        return goodsInStockDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GoodsInStockDTO update(GoodsInStockDTO goodsInStockDTO, Long adminId) throws ServiceException {
        Wrapper<InStockSpuDO> wrapper = new EntityWrapper<>();
        wrapper.like("in_stock_numbers", goodsInStockDTO.getInStockNumbers());
        if (inStockSpuMapper.delete(wrapper) <= 0) {
            throw new AdminServiceException(ExceptionDefinition.GOODS_IN_Delete);
        }
        //???????????????????????????
        List<InStockSpuDO> inStockSpuDOS = goodsInStockDTO.getInStockSpuDOS();
        if (!CollectionUtils.isEmpty(inStockSpuDOS)) {
            for (InStockSpuDO inStockSpuDO : inStockSpuDOS) {
                if (inStockSpuMapper.insert(inStockSpuDO) <= 0) {
                    throw new AdminServiceException(ExceptionDefinition.GOODS_IN_INSERT);
                }
            }
        }
        GoodsInStockDO goodsInStockDO = new GoodsInStockDO();
        BeanUtils.copyProperties(goodsInStockDTO, goodsInStockDO);
        goodsInStockDO.setGmtUpdate(new Date());
        if (goodsInStockMapper.updateById(goodsInStockDO) > 0) {
            return goodsInStockDTO;
        }
        throw new AdminServiceException(ExceptionDefinition.ADMIN_UNKNOWN_EXCEPTION);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String updateInOfStock(String ingoingPerson, Long adminId, Long storageId, String inStockNumbers) throws ServiceException {
        if (StringUtils.isEmpty(storageId) && StringUtils.isEmpty(StringUtils.isEmpty(inStockNumbers))) {
            throw new AdminServiceException(ExceptionDefinition.GOODS_ID_NOT);
        }
        //????????????????????????????????????????????????
        Wrapper<InStockSpuDO> wrapper = new EntityWrapper<>();
        wrapper.like("in_stock_numbers", inStockNumbers);
        List<InStockSpuDO> inStockSpuDOS = inStockSpuMapper.selectList(wrapper);
        Long inStockNum;//????????????
        for (InStockSpuDO inStockSpuDO : inStockSpuDOS) {
            inStockNum = inStockSpuDO.getInStockNum();
            Integer skuId = inStockSpuDO.getSkuId();
            if (stockMapper.updateSockForAdd(storageId, skuId, inStockNum) <= 0) {
                throw new AdminServiceException(ExceptionDefinition.GOODS_NOT_STOCK);
            }
        }
        //??????????????????
        GoodsInStockDO goodsInStockDO = new GoodsInStockDO();
        goodsInStockDO.setStates(GoodsInStockType.IN_FOR_STOCK.getCode());
        goodsInStockDO.setIngoingPerson(ingoingPerson);
        goodsInStockDO.setIngoingTime(new Date());
        goodsInStockDO.setGmtUpdate(new Date());
        Wrapper<GoodsInStockDO> wrapper1 = new EntityWrapper<>();
        wrapper1.like("inStockNumbers", inStockNumbers);
        if (goodsInStockMapper.update(goodsInStockDO, wrapper1) <= 0) {
            throw new AdminServiceException(ExceptionDefinition.GOODS_STOCK_FALSE);
        }
        return "ok";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String delete(Long adminId, Long id, String inStockNumbers) throws ServiceException {
        //??????????????????
        if (goodsInStockMapper.deleteById(id) <= 0) {
            throw new AdminServiceException(ExceptionDefinition.GOODS_DELETE);
        }
        //????????????????????????
        Wrapper<InStockSpuDO> wrapper = new EntityWrapper<>();
        wrapper.like("in_stock_numbers", inStockNumbers);
        if (inStockSpuMapper.delete(wrapper) <= 0) {
            throw new AdminServiceException(ExceptionDefinition.GOODS_IN_SPU_DELETE);
        }
        return "ok";
    }


    @Override
    public List<StorageDO> storagAllName(Long adminId) throws ServiceException {
        int state = StorageStatusType.NOMRAL.getCode();
        List<StorageDO> storageDOS = storageMapper.getStorageNameAll(state);
        return storageDOS;
    }
}
