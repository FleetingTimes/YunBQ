package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.Note;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NoteMapper extends BaseMapper<Note> {
}