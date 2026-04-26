package com.pkh.cloud.auth.domain.service.dept;

import cn.hutool.core.collection.CollUtil;

import com.pkh.cloud.auth.application.enums.CommonStatusEnum;
import com.pkh.cloud.auth.application.vo.dept.PostPageReqVO;
import com.pkh.cloud.auth.application.vo.dept.PostSaveReqVO;
import com.pkh.cloud.auth.domain.entity.dept.PostDO;
import com.pkh.cloud.auth.domain.mapper.dept.PostMapper;
import com.bone.core.exception.BizException;
import com.bone.core.result.PageResult;
import com.bone.core.util.BeanUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.pkh.cloud.auth.application.enums.ErrorCodeConstants.*;
import static com.bone.core.util.CollectionUtils.convertMap;


/**
 * 岗位 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
public class PostServiceImpl implements PostService {

    @Resource
    private PostMapper postMapper;

    @Override
    public Long createPost(PostSaveReqVO createReqVO) {
        // 校验正确性
        validatePostForCreateOrUpdate(null, createReqVO.getName(), createReqVO.getCode());

        // 插入岗位
        PostDO post = BeanUtils.toBean(createReqVO, PostDO.class);
        postMapper.insert(post);
        return post.getId();
    }

    @Override
    public void updatePost(PostSaveReqVO updateReqVO) {
        // 校验正确性
        validatePostForCreateOrUpdate(updateReqVO.getId(), updateReqVO.getName(), updateReqVO.getCode());

        // 更新岗位
        PostDO updateObj = BeanUtils.toBean(updateReqVO, PostDO.class);
        postMapper.updateById(updateObj);
    }

    @Override
    public void deletePost(Long id) {
        // 校验是否存在
        validatePostExists(id);
        // 删除部门
        postMapper.deleteById(id);
    }

    private void validatePostForCreateOrUpdate(Long id, String name, String code) {
        // 校验自己存在
        validatePostExists(id);
        // 校验岗位名的唯一性
        validatePostNameUnique(id, name);
        // 校验岗位编码的唯一性
        validatePostCodeUnique(id, code);
    }

    private void validatePostNameUnique(Long id, String name) {
        PostDO post = postMapper.selectByName(name);
        if (post == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的岗位
        if (id == null) {
            throw new BizException(POST_NAME_DUPLICATE.getCode(), POST_NAME_DUPLICATE.getMsg());
        }
        if (!post.getId().equals(id)) {
            throw new BizException(POST_NAME_DUPLICATE.getCode(), POST_NAME_DUPLICATE.getMsg());
        }
    }

    private void validatePostCodeUnique(Long id, String code) {
        PostDO post = postMapper.selectByCode(code);
        if (post == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的岗位
        if (id == null) {
            throw new BizException(POST_CODE_DUPLICATE.getCode(), POST_CODE_DUPLICATE.getMsg());
        }
        if (!post.getId().equals(id)) {
            throw new BizException(POST_CODE_DUPLICATE.getCode(), POST_CODE_DUPLICATE.getMsg());
        }
    }

    private void validatePostExists(Long id) {
        if (id == null) {
            return;
        }
        if (postMapper.selectById(id) == null) {
            throw new BizException(POST_NOT_FOUND.getCode(), POST_NOT_FOUND.getMsg());
        }
    }

    @Override
    public List<PostDO> getPostList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return postMapper.selectBatchIds(ids);
    }

    @Override
    public List<PostDO> getPostList(Collection<Long> ids, Collection<Integer> statuses) {
        return postMapper.selectList(ids, statuses);
    }

    @Override
    public PageResult<PostDO> getPostPage(PostPageReqVO reqVO) {
        return postMapper.selectPage(reqVO);
    }

    @Override
    public PostDO getPost(Long id) {
        return postMapper.selectById(id);
    }

    @Override
    public void validatePostList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        // 获得岗位信息
        List<PostDO> posts = postMapper.selectBatchIds(ids);
        Map<Long, PostDO> postMap = convertMap(posts, PostDO::getId);
        // 校验
        ids.forEach(id -> {
            PostDO post = postMap.get(id);
            if (post == null) {
                throw new BizException(POST_NOT_FOUND.getCode(), POST_NOT_FOUND.getMsg());
            }
            if (!CommonStatusEnum.ENABLE.getStatus().equals(post.getStatus())) {
                throw new BizException(POST_NOT_ENABLE.getCode(), POST_NOT_ENABLE.getMsg() + post.getName());
            }
        });
    }
}
