#ifndef VIE_SVMP_FBSTREAM_H_RGVHTZRP
#define VIE_SVMP_FBSTREAM_H_RGVHTZRP
#define START 1
#define PLAY  2
#define PAUSE 3
#define STOP  4
#define PRINTSDP 5

struct svmp_fbstream_init_t {
	char IP[16];
	int vidport;
	int audport;
};
struct svmp_fbstream_event_t {
	int cmd;
	long sessid; /* future use */
};



#endif /* end of include guard: VIE_SVMP_FBSTREAM_H_RGVHTZRP */

