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

    private long twepoch = 1288834974657L; //��ʼʱ����������õ�ǰʱ�����ȥ���ʱ��������ƫ����

    private long workerIdBits = 5L; //workerIdռ�õ�λ����5
    private long datacenterIdBits = 5L; //datacenterIdռ�õ�λ����5
    private long maxWorkerId = -1L ^ (-1L << workerIdBits);  // workerId����ʹ�õ������ֵ��31
	// ����˳���ǣ�
	//
	// -1 ���� 5���ý��a
	// -1 ��� a
	// long maxWorkerId = -1L ^ (-1L << 5L)�Ķ���������������£�
	//
	// -1 ���� 5���ý��a ��
	//
	// 11111111 11111111 11111111 11111111 //-1�Ķ����Ʊ�ʾ�����룩
	// 11111 11111111 11111111 11111111 11100000 //��λ����Ĳ�Ҫ����λ��0
	// 11111111 11111111 11111111 11100000 //���a
	// -1 ��� a ��
	//
	// 11111111 11111111 11111111 11111111 //-1�Ķ����Ʊ�ʾ�����룩
	// ^ 11111111 11111111 11111111 11100000 //������������λ�У���ͬ��Ϊ0����ͬ��Ϊ1
	// ---------------------------------------------------------------------------
	// 00000000 00000000 00000000 00011111 //���ս��31
	// ���ս����31��������00000000 00000000 00000000 00011111תʮ���ƿ�����ô�㣺
	// 24+23+22+21+20=16+8+4+2+1=31
	// �Ǽ�Ȼ����֪�������long maxWorkerId = -1L ^ (-1L << 5L)�е�maxWorkerId =
	// 31����ʲô���壿ΪʲôҪ������5���㣿����㿴���������֣����ҵ�������ݿ�����
	//
	// 5λ��bit�����Ա�ʾ�������������25?1=31����������0��1��2��3��....31��32�����֣�����ʾ��ͬ��datecenterId��workerId
	// -1L ^ (-1L << 5L)�����31��25?1�Ľ��Ҳ��31�������ڴ����У�-1L ^ (-1L <<
	// 5L)��д��������λ��������5λ�ܱ�ʾ������������Ƕ���

    private long maxDatacenterId = -1L ^ (-1L << datacenterIdBits); // datacenterId����ʹ�õ������ֵ��31
    private long sequenceBits = 12L;//���к�ռ�õ�λ����12

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
		//��ȡ��ǰʱ��
		long timestamp = timeGen();
		//���ʱ�����޻ع�
		if (timestamp < lastTimestamp) {
			System.err.printf(
					"clock is moving backwards.  Rejecting requests until %d.",
					lastTimestamp);
			throw new RuntimeException(
					String.format(
							"Clock moved backwards.  Refusing to generate id for %d milliseconds",
							lastTimestamp - timestamp));
		}
		//ͬһ����
		if (lastTimestamp == timestamp) {
			sequence = (sequence + 1) & sequenceMask;

			// long seqMask = -1L ^ (-1L << 12L); //����12λ���ʹ洢��������������൱�ڣ�2^12-1 =
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
		
		//�ṹ���64bit
//		1λ�����á������������λΪ1�Ķ��Ǹ����������������ɵ�idһ�㶼ʹ������������������λ�̶���0
//		41λ��������¼ʱ��������룩��
//
//		41λ���Ա�ʾ241?1�����֣�
//		���ֻ������ʾ�����������������������0�������Ա�ʾ����ֵ��Χ�ǣ�0 �� 241?1����1����Ϊ�ɱ�ʾ����ֵ��Χ�Ǵ�0��ʼ��ģ�������1��
//		Ҳ����˵41λ���Ա�ʾ241?1�������ֵ��ת���ɵ�λ������(241?1)/(1000?60?60?24?365)=69��
//		10λ��������¼��������id��
//
//		���Բ�����210=1024���ڵ㣬����5λdatacenterId��5λworkerId
//		5λ��bit�����Ա�ʾ�������������25?1=31����������0��1��2��3��....31��32�����֣�����ʾ��ͬ��datecenterId��workerId
//		12λ�����кţ�������¼ͬ�����ڲ����Ĳ�ͬid��
//
//		12λ��bit�����Ա�ʾ�������������212?1=4095����������0��1��2��3��....4094��4095�����֣�����ʾͬһ����ͬһʱ��أ�����)�ڲ�����4095��ID���
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

	// ---------------����---------------
	public static void main(String[] args) {
		IdWorker worker = new IdWorker(1, 1, 1);
		for (int i = 0; i < 30; i++) {
			System.out.println(worker.nextId());
		}
	}

}