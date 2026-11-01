package io.cranial.platform.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.cranial.platform.conversation.entity.ConversationRetentionPolicyEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationRetentionPolicyMapper extends BaseMapper<ConversationRetentionPolicyEntity> {
}

