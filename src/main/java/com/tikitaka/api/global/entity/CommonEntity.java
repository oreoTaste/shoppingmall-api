package com.tikitaka.api.global.entity;

import java.sql.Date;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommonEntity {
	protected Date insertAt;
	protected Long insertId;
	protected Date modifiedAt;
	protected Long updateId;
}
