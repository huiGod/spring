package com.luban.dao;

import org.springframework.stereotype.Component;

@Component
public class DaoImpl implements Dao {

	@Override
	public void query() {
		System.out.println("query");
	}
}
