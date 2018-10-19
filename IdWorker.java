public class IdWorker {

	private long workerId;
	private long datacenterId;
	private long sequence;

	public IdWorker(long workerId, long datacenterId, long sequence) {
		// sanity check for workerId
		if (workerId > maxWorkerId || workerId < 0) {
			throw new IllegalArgumentException(String.format(
					"worker Id can't be greater than %d or less than 0",
					maxWorkerId));
		}
		if (datacenterId > maxDatacenterId || datacenterId < 0) {
			throw new IllegalArgumentException(String.format(
					"datacenter Id can't be greater than %d or less than 0",
					maxDatacenterId));
		}
		System.out
				.printf("worker starting. timestamp left shift %d, datacenter id bits %d, worker id bits %d, sequence bits %d, workerid %d",
						timestampLeftShift, datacenterIdBits, workerIdBits,
						sequenceBits, workerId);

		this.workerId = workerId;
		this.datacenterId = datacenterId;
		this.sequence = sequence;
	}

    private long twepoch = 1288834974657L; //起始时间戳，用于用当前时间戳减去这个时间戳，算出偏移量

    private long workerIdBits = 5L; //workerId占用的位数：5
    private long datacenterIdBits = 5L; //datacenterId占用的位数：5
    private long maxWorkerId = -1L ^ (-1L << workerIdBits);  // workerId可以使用的最大数值：31
	// 运行顺序是：
	//
	// -1 左移 5，得结果a
	// -1 异或 a
	// long maxWorkerId = -1L ^ (-1L << 5L)的二进制运算过程如下：
	//
	// -1 左移 5，得结果a ：
	//
	// 11111111 11111111 11111111 11111111 //-1的二进制表示（补码）
	// 11111 11111111 11111111 11111111 11100000 //高位溢出的不要，低位补0
	// 11111111 11111111 11111111 11100000 //结果a
	// -1 异或 a ：
	//
	// 11111111 11111111 11111111 11111111 //-1的二进制表示（补码）
	// ^ 11111111 11111111 11111111 11100000 //两个操作数的位中，相同则为0，不同则为1
	// ---------------------------------------------------------------------------
	// 00000000 00000000 00000000 00011111 //最终结果31
	// 最终结果是31，二进制00000000 00000000 00000000 00011111转十进制可以这么算：
	// 24+23+22+21+20=16+8+4+2+1=31
	// 那既然现在知道算出来long maxWorkerId = -1L ^ (-1L << 5L)中的maxWorkerId =
	// 31，有什么含义？为什么要用左移5来算？如果你看过概述部分，请找到这段内容看看：
	//
	// 5位（bit）可以表示的最大正整数是25?1=31，即可以用0、1、2、3、....31这32个数字，来表示不同的datecenterId或workerId
	// -1L ^ (-1L << 5L)结果是31，25?1的结果也是31，所以在代码中，-1L ^ (-1L <<
	// 5L)的写法是利用位运算计算出5位能表示的最大正整数是多少

    private long maxDatacenterId = -1L ^ (-1L << datacenterIdBits); // datacenterId可以使用的最大数值：31
    private long sequenceBits = 12L;//序列号占用的位数：12

    private long workerIdShift = sequenceBits; // 12
    private long datacenterIdShift = sequenceBits + workerIdBits; // 12+5 = 17
    private long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits; // 12+5+5 = 22
    private long sequenceMask = -1L ^ (-1L << sequenceBits);//4095

    private long lastTimestamp = -1L;
    
	public long getWorkerId() {
		return workerId;
	}

	public long getDatacenterId() {
		return datacenterId;
	}

	public long getTimestamp() {
		return System.currentTimeMillis();
	}

	public synchronized long nextId() {
		//获取当前时间
		long timestamp = timeGen();
		//检测时间有无回滚
		if (timestamp < lastTimestamp) {
			System.err.printf(
					"clock is moving backwards.  Rejecting requests until %d.",
					lastTimestamp);
			throw new RuntimeException(
					String.format(
							"Clock moved backwards.  Refusing to generate id for %d milliseconds",
							lastTimestamp - timestamp));
		}
		//同一毫秒
		if (lastTimestamp == timestamp) {
			sequence = (sequence + 1) & sequenceMask;

			// long seqMask = -1L ^ (-1L << 12L); //计算12位能耐存储的最大正整数，相当于：2^12-1 =
			// 4095
			// System.out.println("seqMask: "+seqMask);
			// System.out.println(1L & seqMask);
			// System.out.println(2L & seqMask);
			// System.out.println(3L & seqMask);
			// System.out.println(4L & seqMask);
			// System.out.println(4095L & seqMask);
			// System.out.println(4096L & seqMask);
			// System.out.println(4097L & seqMask);
			// System.out.println(4098L & seqMask);
			//
			//
			// /**
			// seqMask: 4095
			// 1
			// 2
			// 3
			// 4
			// 4095
			// 0
			// 1
			// 2
			// */

			if (sequence == 0) {
				timestamp = tilNextMillis(lastTimestamp);
			}
		} else {
			sequence = 0;
		}

		lastTimestamp = timestamp;
		return ((timestamp - twepoch) << timestampLeftShift)
				| (datacenterId << datacenterIdShift)
				| (workerId << workerIdShift) | sequence;
		
		//结构组成64bit
//		1位，不用。二进制中最高位为1的都是负数，但是我们生成的id一般都使用整数，所以这个最高位固定是0
//		41位，用来记录时间戳（毫秒）。
//
//		41位可以表示241?1个数字，
//		如果只用来表示正整数（计算机中正数包含0），可以表示的数值范围是：0 至 241?1，减1是因为可表示的数值范围是从0开始算的，而不是1。
//		也就是说41位可以表示241?1个毫秒的值，转化成单位年则是(241?1)/(1000?60?60?24?365)=69年
//		10位，用来记录工作机器id。
//
//		可以部署在210=1024个节点，包括5位datacenterId和5位workerId
//		5位（bit）可以表示的最大正整数是25?1=31，即可以用0、1、2、3、....31这32个数字，来表示不同的datecenterId或workerId
//		12位，序列号，用来记录同毫秒内产生的不同id。
//
//		12位（bit）可以表示的最大正整数是212?1=4095，即可以用0、1、2、3、....4094这4095个数字，来表示同一机器同一时间截（毫秒)内产生的4095个ID序号
	}

	private long tilNextMillis(long lastTimestamp) {
		long timestamp = timeGen();
		while (timestamp <= lastTimestamp) {
			timestamp = timeGen();
		}
		return timestamp;
	}

	private long timeGen() {
		return System.currentTimeMillis();
	}

	// ---------------测试---------------
	public static void main(String[] args) {
		IdWorker worker = new IdWorker(1, 1, 1);
		for (int i = 0; i < 30; i++) {
			System.out.println(worker.nextId());
		}
	}

}