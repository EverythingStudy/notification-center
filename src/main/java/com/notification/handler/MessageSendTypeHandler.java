package com.notification.handler;

import com.notification.model.enums.MessageSendTypeEnum;
import org.apache.ibatis.type.EnumOrdinalTypeHandler;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(MessageSendTypeEnum.class)
public class MessageSendTypeHandler extends EnumOrdinalTypeHandler<MessageSendTypeEnum> {

    public MessageSendTypeHandler() {
        super(MessageSendTypeEnum.class);
    }
}
