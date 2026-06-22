package com.notification.handler;

import com.notification.model.enums.MessageStatusEnum;
import org.apache.ibatis.type.EnumOrdinalTypeHandler;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(MessageStatusEnum.class)
public class MessageStatusTypeHandler extends EnumOrdinalTypeHandler<MessageStatusEnum> {

    public MessageStatusTypeHandler() {
        super(MessageStatusEnum.class);
    }
}
