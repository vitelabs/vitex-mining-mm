package org.vite.dex.mm.model.pojo.http;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultBean<T> {

    private int code;

    private String msg;

    private T data;
}
