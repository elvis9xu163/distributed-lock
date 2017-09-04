package com.xjd.distributed.lock2.zoo;

import lombok.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @author elvis.xu
 * @since 2017-08-30 10:35
 */
@Getter
@Setter
@EqualsAndHashCode
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LockSettings {
	protected int maxConcurrent;
	protected int maxQueue;
	protected long maxExpireInMills;
}
