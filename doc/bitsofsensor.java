
            List<String> strings=Files.readAllLines(Paths.get("/run/moisture.txt"), Charset.forName("UTF-8"));
            LinkedList<Integer> values=new LinkedList<Integer>();
            for(String s: strings) values.add(Integer.parseInt(s));

            Collections.sort(values);

            int sum=0; int num=values.size()/2;
            for(int i=0; i<num; i++) sum+=values.get(i);
            final int ave=sum/num;





    void doitInJavaOrNot(){
        while(true){
            int loops=20;
            int hold=50;
            long startTime=System.nanoTime();
            int waitCount=0;
            for(int l=0; l<loops; l++){
                PiUtils.setGPIODir("4", "out");
                PiUtils.setGPIOVal("4", false);
                Kernel.sleep(hold);
                PiUtils.setGPIODir("4", "in");
                while(PiUtils.getGPIOVal("4")!='1' && waitCount<2000) waitCount++;
                if(waitCount==2000){ logXX("waited too long"); waitCount=0; break; }
            }
            if(waitCount==0){ logXX("unstable"); continue; }

            int microseconds=(int)((System.nanoTime()-startTime)/loops)/1000 - hold*1000 - 2900;

            if(microseconds/waitCount>20){ logXX("unstable", waitCount, "its", microseconds/waitCount, "stability", microseconds, "uS"); continue; }

            smoothmicros=((85*smoothmicros)+(15*microseconds))/100;

            int picofarads=smoothmicros/5;

            double mst=(picofarads-120)/10; if(mst<0) mst=0; if(mst>100) mst=100;

            logXX(waitCount, "its", microseconds/waitCount, "stability", microseconds, "uS", smoothmicros, "uS", picofarads, "pF", mst, "%");

            final double moisture=mst;
            new Evaluator(this){ public void evaluate(){
                contentDouble("soil-moisture", moisture);
                content("text", String.format("Soil Moisture: %.0f %%", moisture));
            }};
        }
    }
