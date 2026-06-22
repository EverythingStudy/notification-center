package com.notification.mapper;

import com.notification.model.entity.UserFeedCursor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 用户 Feed 游标 Mapper
 */
@Mapper
public interface UserFeedCursorMapper {

    int insert(UserFeedCursor cursor);

    int updateCursor(@Param("userId") Long userId,
                     @Param("feedType") String feedType,
                     @Param("cursor") Long cursor);

    Optional<UserFeedCursor> findByUserIdAndFeedType(
            @Param("userId") Long userId,
            @Param("feedType") String feedType);

    List<UserFeedCursor> findByUserId(@Param("userId") Long userId);

    /**
     * 获取用户所有 Feed 的游标
     */
    List<UserFeedCursor> findByUserIdAndFeedTypes(
            @Param("userId") Long userId,
            @Param("feedTypes") List<String> feedTypes);
}
