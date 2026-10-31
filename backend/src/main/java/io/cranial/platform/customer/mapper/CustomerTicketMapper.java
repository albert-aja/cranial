package io.cranial.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.cranial.platform.customer.entity.CustomerTicketEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerTicketMapper extends BaseMapper<CustomerTicketEntity> {
}

