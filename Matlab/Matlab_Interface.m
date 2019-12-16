clear all
close all
clc
if ~isempty(instrfind)
     fclose(instrfind);
      delete(instrfind);
end

s = serial('COM6');
fopen(s)

parse_config_packet(s)


% s = Bluetooth('Phoenix BT Link',1);
% fopen(s);
    
s = serial('COM6');
fopen(s)
while(1)
    m = ' ';
    while(m~='$')
        m = rcv_uart(s);
    end
    a = [];
    for i=1:5
        a(i) = uint8(rcv_uart(s));
    end
    a(a>127) = a(a>127)-256;
    a
%     char(a)
%     (rcv_uart(s))
%     thr = uint8(rcv_uart(s));
%     yaw = uint8(rcv_uart(s));
%     pitch = uint8(rcv_uart(s));
%     roll = uint8(rcv_uart(s));
%     chksum = uint8(rcv_uart(s));
%     fprintf('%d, %d, %d, %d, %d\n', thr,yaw,pitch,roll,chksum);
end



% out = fscanf(s)

joy = vrjoystick(1);
c = caps(joy)%Joystick capabilities

while(1)
%     b = button(joy, n);
% 	[axes, buttons, povs] = read(joy);
% force(joy, n, f); %applies force feedback to joystick axis n.
% p = pov(joy, n);
%     brake = axis(joy, 2);
%     accelerator = axis(joy,3);
%     steering = axis(joy,4);
    thr_cmd = 25;
    trn_cmd = 128;
    pitch_cmd = 128;
    roll_cmd = 128;
    chksm = thr_cmd + trn_cmd + pitch_cmd + roll_cmd;
    
%     fwrite(s, uint8('$'));
%     fwrite(s, uint8(thr_cmd));
%     fwrite(s, uint8(trn_cmd));
%     fwrite(s, uint8(pitch_cmd));
%     fwrite(s, uint8(roll_cmd));
%     fwrite(s, uint8(chksm));

%     fprintf(s,'\n')
%     pause(1)
%     fprintf(s,'hello from matlab')
pause(0.1)
end

close(joy);
fclose(s)
delete(s)
clear s

function parse_config_packet(s)
    while(1)
        m = ' ';
        while(1)
            m = rcv_uart(s);
            if(m=='#')
                m = rcv_uart(s);
                if(m=='C')
                    m = rcv_uart(s);
                    if(m=='G')
                    break;%header found
                    end
                end
            end
        end
        a = [];
        for i=1:72
            a(i) = uint8(rcv_uart(s));
        end
        
        v = zeros(1,18);
        for j=1:18
            b = uint8(a((j-1)*4+1:(j-1)*4+4));
            v(j) = typecast(fliplr(b),'single');
            fprintf('%f\t', v(j));
        end
            fprintf('\n');
    
    end
end

function m = rcv_uart(s)
    while(1)
        [m,n] = fscanf(s,'%c',1);
        if(n>0)
            break;
        end
    end
end
