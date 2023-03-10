package com.kxmall.market.biz.service.config;

import com.alibaba.fastjson.JSONObject;
import com.kxmall.market.biz.service.storage.StorageBizService;
import com.kxmall.market.biz.service.user.UserBizService;
import com.kxmall.market.core.Const;
import com.kxmall.market.core.exception.ExceptionDefinition;
import com.kxmall.market.core.exception.ServiceException;
import com.kxmall.market.core.exception.ThirdPartServiceException;
import com.kxmall.market.core.util.GeneratorUtil;
import com.kxmall.market.data.component.CacheComponent;
import com.kxmall.market.data.domain.ConfigDO;
import com.kxmall.market.data.dto.ConfigDTO;
import com.kxmall.market.data.mapper.ConfigMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Created by admin on 2019/7/21.
 */
@Service
public class ConfigBizService {

    @Autowired
    private ConfigMapper configMapper;

    @Autowired
    private CacheComponent cacheComponent;

    @Autowired
    private UserBizService userBizService;

    @Autowired
    private StorageBizService storageBizService;

    private OkHttpClient okHttpClient = new OkHttpClient();

    private static final String CA_CONFIG_DTO_KEY = "CA_CONFIG_DTO_KEY";

    private static final String GEN_QR_CODE_URL = "https://api.weixin.qq.com/cgi-bin/wxaapp/createwxaqrcode?access_token=";

    private static final Logger logger = LoggerFactory.getLogger(ConfigBizService.class);

    public ConfigDTO getMerchantConfig() {
        ConfigDTO obj = cacheComponent.getObj(CA_CONFIG_DTO_KEY, ConfigDTO.class);
        if (obj != null) {
            return obj;
        }
        List<ConfigDO> list = configMapper.selectList(null);
        ConfigDTO configDTO = new ConfigDTO();
        for (ConfigDO configDO : list) {
            switch (configDO.getKeyWord()) {
                case "title":
                    configDTO.setTitle(configDO.getValueWorth());
                    break;
                case "logoUrl":
                    configDTO.setLogoUrl(configDO.getValueWorth());
                    break;
                case "description":
                    configDTO.setDescription(configDO.getValueWorth());
                    break;
                case "address":
                    configDTO.setAddress(configDO.getValueWorth());
                    break;
                case "showType":
                    configDTO.setShowType(Integer.parseInt(configDO.getValueWorth()));
                    break;
                case "status":
                    configDTO.setStatus(Integer.parseInt(configDO.getValueWorth()));
                    break;
            }
        }
        cacheComponent.putObj(CA_CONFIG_DTO_KEY, configDTO, Const.CACHE_ONE_DAY);
        return configDTO;
    }

    public void clearMerchantConfigCache() {
        cacheComponent.del(CA_CONFIG_DTO_KEY);
    }

    /**
     * ??????????????????????????????
     * @param path ???????????????????????????
     * @return
     */
    public String generateWxMiniCode(String path) throws ServiceException {
        try {
            MediaType mediaType = MediaType.parse("application/json");
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("path", path);
            jsonObject.put("width", 1280);
            RequestBody body = RequestBody.create(mediaType, jsonObject.toJSONString());
            Request request = new Request.Builder()
                    .url(GEN_QR_CODE_URL + userBizService.getWxMiniAccessToken())
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();
            Response response = okHttpClient.newCall(request).execute();
            if (response.code() == 200) {
                InputStream is = response.body().byteStream();
                String url = storageBizService.upload("qr/" + GeneratorUtil.genFileName() + ".png", is, response.body().contentLength(), "image/png");
                return url;
            } else {
                throw new ThirdPartServiceException("?????????????????????", ExceptionDefinition.THIRD_PART_IO_EXCEPTION.getCode());
            }
        } catch (IOException e) {
            logger.error("[????????????????????????] IO??????", e);
            throw new ThirdPartServiceException(ExceptionDefinition.THIRD_PART_IO_EXCEPTION);
        } catch (Exception e) {
            logger.error("[????????????????????????] ??????", e);
            throw new ThirdPartServiceException(ExceptionDefinition.APP_UNKNOWN_EXCEPTION);
        }
    }
}
